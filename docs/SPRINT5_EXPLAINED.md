# Sprint 5 — What Was Built and Why

This document explains every file created or changed in Sprint 5 in plain language — code level, architecture level, and business/domain level. Use it as a reference when reviewing the code, resuming work later, or explaining the project in interviews.

---

## Why Sprint 5 Comes Fifth

Sprint 4 got real content *into* the system: `POST /api/v1/ingestion/documents` → chunks in PostgreSQL + vectors in Qdrant. But nothing could read any of it back yet — `QdrantVectorStoreAdapter.search()` was a deliberate `UnsupportedOperationException` stub, and there was no query endpoint, no retrieval orchestration, no prompt, no LLM call. Sprint 5 is the other half of the RAG loop: `POST /api/v1/visa/query` → grounded answer with citations.

```
VisaQueryController (presentation)
  → VisaQueryService (application/query)
      → HybridRetriever → ReRanker → ConfidenceScorer → VisaPromptTemplate → ChatPort → CitationBuilder
          → QdrantVectorStoreAdapter, DocumentRepository, ChatAdapter (infrastructure)
```

---

## A Mid-Sprint Correction: `search()` Was Still a Stub

Building the application-layer classes (`HybridRetriever` calling `VectorSearchPort.search(...)`) does not make the method underneath them real. Partway through writing up the sprint as "done," a check of `QdrantVectorStoreAdapter.search()` showed it still throwing the Sprint 4 stub's `UnsupportedOperationException` — meaning every real query would have thrown at runtime despite `mvn compile` passing cleanly. Compiling is not the same as working; this is exactly the gap `CLAUDE.md`'s "test the golden path, don't just claim success from a build" principle exists to catch. The adapter was implemented for real before Sprint 5 was called complete (see below).

---

## `application/command/VisaQueryCommand.java` and `application/result/VisaQueryResult.java`

Both mirror an existing domain type's shape exactly rather than inventing new fields — `VisaQueryCommand` mirrors `VisaQuery` (`queryText` + `MetadataFilter`), `VisaQueryResult` mirrors `VisaAnswer` (`answerText`, `citations`, `confidenceScore`). This is deliberate duplication, not an oversight: it keeps the application-layer boundary explicit (a command/result type is what a use case's *caller* deals with; a domain type is what the *use case itself* deals with internally), the same way `IngestDocumentCommand` and `IngestionResult` don't just re-export `Document`. Unlike `IngestionResult` (which adds `documentId`/`status`, data not on `Document` verbatim), neither `VisaQuery` nor `VisaAnswer` carry any extra generated state for this use case, so the mirrored shape ends up identical field-for-field — that's a consequence of the domain model already being exactly the right shape, not a sign the duplication is pointless.

---

## `application/query/retrieval/HybridRetriever.java`

Runs vector search and full-text search **concurrently** (`CompletableFuture`, not sequential) — they're independent I/O calls against two different systems (Qdrant, PostgreSQL), so there's no reason to serialize them.

### Why RRF fuses on rank position, not on each path's raw score

Cosine similarity (Qdrant) and `ts_rank` (PostgreSQL) are on incomparable scales — a 0.82 cosine score and a 0.82 `ts_rank` don't mean the same thing. Reciprocal Rank Fusion sidesteps this entirely by only looking at *where* a chunk lands in each ranked list, not its raw score: `contribution = 1 / (k + rank)`, summed across whichever list(s) a chunk appears in.

### Why `k = 60`

Not an arbitrary choice — `ConfidenceScorer` (Sprint 2) already has a constant, `MAX_EXPECTED_FUSION_SCORE = 0.05`, documented in its own source as assuming "RRF k=60, two retrieval paths." `HybridRetriever` is the class that actually produces the fusion score `ConfidenceScorer` scores — using a different `k` here would silently invalidate an assumption baked into already-shipped, already-tested code.

### What this class deliberately does NOT do

It passes `MetadataFilter` through to both `VectorSearchPort.search()` and `DocumentRepository.fullTextSearch()` completely unchanged — the class's own Javadoc says so explicitly. The `GENERAL`-visa-type fallback (see below) could not be implemented here even if it seemed convenient to; `HybridRetriever` has no visibility into how either port's filter is actually executed.

---

## `application/query/retrieval/ReRanker.java` — A Genuine Fork, Resolved by Asking

Three designs were on the table for turning the RRF-merged list into a final, prompt-ready list: an LLM-based reranker (send candidates back to the chat model to score/reorder), a pure-Java heuristic re-score, or no re-scoring at all (just truncate the already-ranked list). This wasn't a call to make silently — asked directly, and the user picked the heuristic option explicitly over the LLM-based one, despite it being framed as "potentially higher quality."

**What it does:** boosts each chunk's RRF fusion score by its keyword-overlap ratio with the query — `finalScore = fusionScore * (1.0 + overlapRatio)` — then truncates to the top 8. Multiplicative, not additive, so it doesn't need a weight constant to reconcile two very different score scales (RRF's raw numbers are tiny; picking an additive weight would mean guessing how much a full keyword match should "count" in RRF's units). No new port, no extra LLM call, no added latency or cost.

**A known, deliberate duplication:** the tokenization/overlap logic here is almost identical to `ConfidenceScorer.keywordMatchRatio()` in the domain layer. It isn't shared via a `common/util` helper, because doing so would mean modifying that already-shipped, already-tested class outside this class's own scope — flagged here rather than silently copy-pasted, worth revisiting if a third consumer of the same logic ever shows up.

---

## `application/query/prompt/VisaPromptTemplate.java`

Builds a plain `String` prompt — no Spring AI types anywhere in this class. `ChatPort.generate(String prompt, String modelId)` was deliberately designed (Sprint 4) to take a bare string, keeping Spring AI's `Prompt`/`Message` types confined to `infrastructure/llm`; this class honors that boundary.

Encodes `CLAUDE.md`'s RAG principles directly into the system instructions: answer only from the numbered excerpts, cite every claim with its bracket number, and if the excerpts don't support an answer, respond with the exact required sentence — `"I couldn't find sufficient official information."` No special-case code path for an empty chunk list; the instructions already tell the model what to do, and the real gate for "not enough context" is `VisaQueryService`'s confidence-threshold check, not this class.

**A coupling worth knowing about:** excerpts are numbered `[1]`, `[2]`, ... in `context.scoredChunks()` order, and `CitationBuilder` numbers its citations the same way. There's no shared type enforcing this — it works because `VisaQueryService` passes the exact same `RetrievedContext` instance to both. If a future change ever re-sorts or filters the context between these two calls, the bracket numbers in the model's answer would silently point at the wrong source.

---

## `application/query/citation/CitationBuilder.java`

### The gap `Citation` exposed

`Citation` needs `source` (a document title) and `url` (`DocumentMetadata.sourceUrl`) — neither exists on `Chunk` or `ScoredChunk`. The lower-risk fix was to give `CitationBuilder` a dependency on the already-existing `DocumentRepository` and resolve each chunk's parent `Document` by id, caching per `build()` call so chunks sharing a document (common after `ReRanker` truncates to 8) don't trigger duplicate lookups. The alternative — extending `ScoredChunk` a third time — was rejected because it would have rippled into `QdrantVectorStoreAdapter`'s Qdrant payload and the full-text-search SQL join, both already-shipped code, for a citation-only concern.

A chunk with no resolvable parent document is treated as a data-integrity defect (`DocumentNotFoundException`), not a normal outcome — a chunk cannot legitimately outlive the document it belongs to.

---

## `application/query/VisaQueryService.java` — The 11-Step Orchestrator

Build `VisaQuery` from the command → start a latency timer → `HybridRetriever.retrieve` → `ReRanker.rerank` → `ConfidenceScorer.score` → threshold gate → `VisaPromptTemplate.render` → `ChatPort.generate` → `CitationBuilder.build` → `VisaQueryLogRepository.log` (audit) → build `VisaQueryResult`.

### The confidence gate throws; it doesn't build a fallback answer inline

`InsufficientContextException` already existed from Sprint 2, and its Javadoc already described exactly this use ("thrown when the retrieved context's confidence score falls below the configured threshold"). The property backing it (`rag.confidence.threshold=0.60`) already existed too. So the service throws below threshold rather than constructing a placeholder `VisaAnswer` — converting that exception into the required fallback HTTP response is `GlobalExceptionHandler`'s job, which is exactly why that's a separate Sprint 6 line item rather than something Sprint 5 needed to duplicate.

### `ConfidenceScorer` is instantiated directly, not injected

`new ConfidenceScorer()`, not a constructor parameter. It's stateless, has zero dependencies, and is deliberately Spring-unaware (domain layer rule). Wrapping a zero-dependency pure class in a `@Bean` just to satisfy DI would add a config class for no behavioral benefit.

---

## `infrastructure/llm/ChatAdapter.java`

A near one-line delegation to Spring AI's auto-configured `ChatModel.call(String)`, mirroring `EmbeddingAdapter`'s exact pattern from Sprint 4.

`ChatPort.generate` takes a `modelId` parameter, but this class doesn't use it to select anything — the injected `ChatModel` bean always targets whichever single model `spring.ai.openai.chat.options.model` configures, and this project has no per-call model-switching anywhere. `modelId` is accepted purely so `VisaQueryService` can pass it through to `VisaQueryLogRepository.log` for audit traceability — the same "informational, not behavioral" role `EmbeddingModelConfig`'s `embeddingModelId` bean already plays for `EmbeddingAdapter`.

---

## `infrastructure/vectorstore/QdrantVectorStoreAdapter.search()` — Implemented For Real

This is the piece that turned "compiles" into "actually works."

### Verified against the real API, not guessed

Qdrant's Java client is generated gRPC code with builder-heavy APIs that are easy to get subtly wrong from memory. Following the same discipline `SPRINT4_EXPLAINED.md` used for `upsert()`, the exact shapes of `ConditionFactory`, `Points.Filter.Builder`, `Points.SearchPoints.Builder`, and `QdrantClient.searchAsync` were checked with `javap` against the actual `io.qdrant:client:1.9.1` jar before writing any code.

### Why it needs PostgreSQL at all — Qdrant's payload never held chunk text

Looking at `upsert()` (Sprint 4): the payload written per point is `chunk_id`, `document_id`, `passport_iso`, `destination_iso`, `visa_type_code`, `last_updated`, `chunk_index` — never `content`. That's consistent with `CLAUDE.md`'s "Never store business data only in Qdrant" rule; the actual chunk text lives solely in PostgreSQL's `document_chunks.content`. So a Qdrant match only gives a `chunk_id` and a cosine score — building a real `Chunk` (which requires non-blank `content`) means a follow-up lookup. `search()` batches this via `ChunkJpaRepository.findAllById`, keyed by the same `chunk_id` UUID written at upsert time.

A Qdrant point whose `chunk_id` has no matching PostgreSQL row (possible because ingestion has no transaction spanning both stores — a known, already-documented Sprint 4 gap) is logged at `WARN` and skipped, rather than failing the entire search over one inconsistent point.

### The `GENERAL` fallback, finally closed

Flagged as a must-not-miss Sprint 5 dependency since `V8__add_general_visa_type.sql` (Sprint 4): when a specific visa type is requested, the filter must match that type **or** `GENERAL`, not just an exact type. Implemented as a nested `Filter` — `Filter.newBuilder().addShould(matchKeyword("visa_type_code", requestedType)).addShould(matchKeyword("visa_type_code", "GENERAL"))`, wrapped via `ConditionFactory.filter(...)` and added as one more `must` condition alongside the passport/destination filters. When no visa type is requested at all (`null`), no visa-type condition is added — every type, `GENERAL` included, already matches.

The same fix was needed on the full-text-search side (`ChunkJpaRepository.searchFullText`'s native SQL `WHERE` clause) — a one-line addition (`OR vt.code = 'GENERAL'`) to the existing exact-match condition. Both paths needed the fix independently; fixing only one would have left `GENERAL` content reachable by vector search but invisible to keyword search, or vice versa.

---

## `presentation/` — DTOs, Mapper, Controller

Same shape as the Ingestion trio from Sprint 4: `VisaQueryRequest` (`@NotBlank` on `query`/`passport`/`destination`; `visaType` deliberately unvalidated-nullable, matching `MetadataFilter.visaTypeCode`'s "null means all types" semantics), `VisaQueryResponse` (wire-named `answer`, matching the sprint plan's own wording rather than the domain field name `answerText` — same decoupling precedent as `IngestionResponse.status` being a plain string, not a re-exported enum), `VisaQueryMapper`, and a thin `VisaQueryController`.

---

## Files Created / Changed in Sprint 5

```
src/main/java/com/neaz/travel/visa/application/command/
  VisaQueryCommand.java                                        ← new
src/main/java/com/neaz/travel/visa/application/result/
  VisaQueryResult.java                                         ← new
src/main/java/com/neaz/travel/visa/application/query/
  VisaQueryService.java                                        ← new
src/main/java/com/neaz/travel/visa/application/query/retrieval/
  HybridRetriever.java                                         ← new
  ReRanker.java                                                ← new
src/main/java/com/neaz/travel/visa/application/query/prompt/
  VisaPromptTemplate.java                                      ← new
src/main/java/com/neaz/travel/visa/application/query/citation/
  CitationBuilder.java                                         ← new
src/main/java/com/neaz/travel/visa/infrastructure/llm/
  ChatAdapter.java                                              ← new
src/main/java/com/neaz/travel/visa/infrastructure/vectorstore/
  QdrantVectorStoreAdapter.java                                 ← search() implemented (was a stub)
src/main/java/com/neaz/travel/visa/infrastructure/persistence/
  ChunkJpaRepository.java                                       ← GENERAL fallback added to searchFullText SQL
src/main/java/com/neaz/travel/visa/presentation/dto/
  VisaQueryRequest.java                                         ← new
  VisaQueryResponse.java                                        ← new
src/main/java/com/neaz/travel/visa/presentation/mapper/
  VisaQueryMapper.java                                          ← new
src/main/java/com/neaz/travel/visa/presentation/controller/
  VisaQueryController.java                                      ← new
```

---

## How to Verify Sprint 5 Works

```bash
mvn compile
# BUILD SUCCESS — verified during the sprint, including the search()/GENERAL fixes.
```

**Not yet done — the actual end-to-end smoke test**, which needs a running stack:

```bash
docker compose up          # starts PostgreSQL + Qdrant
export OPENAI_API_KEY=...  # required for embedding + chat calls
./mvnw spring-boot:run

# First, ingest something (Sprint 4 endpoint):
curl -X POST http://localhost:8080/api/v1/ingestion/documents \
  -H "Content-Type: application/json" \
  -d @knowledge-base/India/Tourist/metadata.json

# Then query it:
curl -X POST http://localhost:8080/api/v1/visa/query \
  -H "Content-Type: application/json" \
  -d '{"query": "What documents do I need for a tourist visa?", "passport": "BD", "destination": "IN", "visaType": "TOURIST"}'

# Expected: 200 OK, ApiResponse wrapping a VisaQueryResponse with a grounded
# answer, citations[] pointing back to the ingested document, and a
# confidenceScore. A query with no matching ingested content, or one whose
# retrieved context scores below 0.60, should currently surface as an
# unhandled InsufficientContextException (Spring Boot's default error
# response) rather than a clean fallback message — GlobalExceptionHandler is
# Sprint 6 scope.
```

This hasn't been run against a live stack in the session that wrote the code — no Docker daemon or `OPENAI_API_KEY` was available there, the same limitation `SPRINT3_EXPLAINED.md` and `SPRINT4_EXPLAINED.md` both note. Running it locally is the real verification step.