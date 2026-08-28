# Sprint 6 — What Was Built and Why

This document explains every file created or changed in Sprint 6 in plain language — code level, architecture level, and business/domain level. Use it as a reference when reviewing the code, resuming work later, or explaining the project in interviews.

---

## Why Sprint 6 Comes Sixth

By the end of Sprint 5 the RAG loop actually worked end to end — ingest a document, ask a question, get a grounded, cited answer — but only on the happy path. Nothing translated a thrown exception into a client-friendly response (an unhandled `InsufficientContextException` would surface as Spring Boot's default 500 error body, not the required "I couldn't find sufficient official information." sentence). Test coverage was thin — `ConfidenceScorer` had 3 tests, and `HybridRetriever`, `ReRanker`, `VisaPromptTemplate`, and `CitationBuilder` had none. And `docs/api.md`/`README.md` didn't exist at all. Sprint 6 closes all three gaps — error handling, test coverage, documentation — without touching any of Sprint 4/5's actual RAG logic.

```
presentation/controller/GlobalExceptionHandler   ← new
domain/service/ConfidenceScorerTest              ← 3 tests → 16
application/query/citation/CitationBuilderTest   ← new (6)
application/query/retrieval/HybridRetrieverTest  ← new (7)
application/query/prompt/VisaPromptTemplateTest  ← new (7)
RagPipelineIntegrationTest                       ← new (3, Testcontainers)
docs/api.md, README.md                           ← new
```

---

## `presentation/controller/GlobalExceptionHandler.java`

A single `@RestControllerAdvice` so every endpoint — success or failure — returns the same `ApiResponse<T>` envelope. No controller anywhere in the codebase catches exceptions itself; this is the one place that translates thrown exceptions into HTTP responses.

### The low-confidence fallback was folded in here, not built as its own thing

The original plan treated "low-confidence fallback" as separate work from "exception handling." In practice they're the same mechanism: `VisaQueryService` already threw `InsufficientContextException` since Sprint 2 (its Javadoc even already described this exact use case), so the natural place to convert that exception into the required fallback sentence is the same `@RestControllerAdvice` that handles every other exception. Asked directly via `AskUserQuestion`, and confirmed: **the fallback response is `200 OK` / `success: true`**, wrapping a normal `VisaQueryResponse` (fixed answer text, empty citations, the real computed `confidenceScore`) — not a `success: false` error envelope. `CLAUDE.md`'s RAG principles literally say "if context is insufficient, respond with: 'I couldn't find sufficient official information.'" — that's specified as a valid *answer*, not a failure mode, so the HTTP semantics follow that: a RAG system correctly saying "I don't know" is success, not error.

### The rest of the mapping table

| Exception | Status | Rationale |
|---|---|---|
| `InsufficientContextException` | `200` / `success: true` | see above |
| `DocumentNotFoundException` | `404` | a specific, well-known "this id doesn't exist" case |
| `MethodArgumentNotValidException` | `400` | `@Valid` failures; field errors joined into one message (`"query: must not be blank; passport: must not be blank"`) |
| `VisaRagException` (catch-all subtype) | `400` | every other deliberate domain/application exception (e.g. an ingestion path escaping the knowledge-base root) — the message is safe to expose because it was raised deliberately by our own code, not leaked framework internals |
| `Exception` (last resort) | `500` | generic message only (`"An unexpected error occurred. Please try again later."`); full exception is logged server-side via SLF4J, never returned to the client |

The ordering matters for exactly one reason: `DocumentNotFoundException` and `InsufficientContextException` both extend `VisaRagException`, but Spring's `@ExceptionHandler` resolution picks the most specific matching handler regardless of declaration order — so this isn't actually order-dependent, just organized most-specific-first for readability.

---

## Test Expansion

### `ConfidenceScorerTest` — 3 tests → 16

The original 3 tests (above/below threshold, roughly) didn't actually pin down *why* a score landed where it did. The expanded suite isolates each of the four weighted factors — fusion score (0.50), chunk count (0.20), keyword match (0.20), recency (0.10) — one at a time, by pinning the other three at their maximum contribution and asserting an exact hand-computed total (e.g. `score_fusionScoreBelowMax_scalesLinearly` expects exactly `0.75`, not just "some number below 1.0"). This is a stronger test than "the score is roughly right" — it proves each factor's *shape* (linear scaling, capping at the max, zero at the boundary) independently, so a regression in one factor can't hide behind the other three compensating for it. Boundary cases get their own tests too: recency at exactly the 365-day full-score cutoff, the 3-year midpoint, and the 5-year zero-score cutoff, matching the exact constants documented in `ConfidenceScorer`'s own Javadoc from Sprint 2.

### `CitationBuilderTest` (6 tests)

Covers the sequential numbering contract with `VisaPromptTemplate` (`[1]`, `[2]`, ... in `scoredChunks()` order), the per-`build()`-call document cache (`build_withMultipleChunksFromSameDocument_looksUpDocumentOnlyOnce` — proves the dedup Sprint 5 added actually works, via a Mockito `verify(times(1))`), the `DocumentNotFoundException` thrown for a chunk whose parent document can't be resolved, and the empty-input short-circuit that skips the repository entirely rather than making a pointless call.

### `HybridRetrieverTest` (7 tests)

The one worth calling out: `retrieve_chunkRankedInBothPaths_outranksChunkFromOnlyOnePathViaCombinedRrfScore` — this is the actual mathematical proof that Reciprocal Rank Fusion is doing its job, not just "some fusion happened." A chunk ranked #2 on *both* the vector and full-text paths should score higher than a chunk ranked #1 on only one path, because RRF sums `1/(k+rank)` across every path a chunk appears in. That's a genuinely non-obvious property of RRF (a lower individual rank can still win via consensus across paths), and it's exactly the property the whole hybrid-search design depends on — a test that only checked "both paths get called" would miss a broken fusion formula entirely.

### `VisaPromptTemplateTest` (7 tests)

Structural assertions only — checks that the rendered prompt numbers excerpts correctly, includes the question text, ends with the expected answer cue, and contains the citation-instruction and no-hallucination-fallback text — without duplicating the full instructions string into the test (a brittle "does the prompt equal this giant literal" test would break on every unrelated wording tweak).

---

## `RagPipelineIntegrationTest` — Automating the Sprint 5 Manual Smoke Test

`docs/SPRINT5_SMOKE_TEST_REPORT.md` already proved the pipeline works, by hand, once. This test automates that same coverage — happy path, `GENERAL` visa-type fallback on both retrieval paths, and low-confidence fallback — so it runs on every `mvn test`, not just once.

### Real stores, stubbed models

Real PostgreSQL and Qdrant via Testcontainers (`GenericContainer` for Qdrant — no new Maven dependency needed, the raw gRPC client the project already uses talks to it directly). Only `EmbeddingPort` and `ChatPort` are stubbed: a real embedding/chat call would make the suite network-dependent, require an API key in CI, and be non-deterministic. The `KeywordEmbeddingPort` stub maps a fixed set of keywords to basis-vector dimensions, so real Qdrant cosine similarity search still behaves predictably against it — everything downstream of the stub (persistence, vector search, full-text search, RRF fusion, reranking, confidence scoring, prompt rendering, citation building, the controller/mapper layer) is the real production code.

### The non-obvious bug this test caught: cross-thread transaction visibility

`@DataJpaTest`'s default behavior wraps each test in a transaction and rolls it back afterward — normally convenient, since it means no manual cleanup. But `HybridRetriever` runs its two retrieval paths concurrently via `CompletableFuture` (Sprint 5's design, for good reason — they're independent I/O calls). A thread other than the one holding the test's transaction cannot see that transaction's uncommitted writes, so a chunk ingested on the main test thread would be invisible to a retrieval path running on a `CompletableFuture`'s worker thread — not because retrieval was broken, but because the test's own isolation mechanism was fighting the code under test. Fixed with `@Transactional(propagation = Propagation.NOT_SUPPORTED)`, which disables the wrapping transaction entirely so every repository call commits immediately and is visible to any thread — and a manual `@AfterEach tearDown()` that deletes chunks/documents/query-audit rows to compensate for the rollback safety net this trades away.

### The three tests

- `ingestThenQuery_returnsGroundedAnswerWithCitationAboveConfidenceThreshold` — the full happy path: ingest a real tourist-visa document, ask a matching question, assert a grounded answer with a citation and confidence above threshold.
- `query_whenGeneralDocumentExists_includesItAlongsideTheRequestedVisaTypeOnBothRetrievalPaths` — regression-proofs the Sprint 5 `GENERAL` fallback fix on both the vector and full-text paths simultaneously, not just one.
- `query_whenNoDocumentsMatch_throwsInsufficientContext_andHandlerReturnsTheFallbackResponse` — proves the `GlobalExceptionHandler` mapping end to end: no matching documents → `InsufficientContextException` → `200`/`success: true` with the fixed fallback sentence, exercised through the real controller and handler, not a unit-level exception assertion.

---

## `docs/api.md` and `README.md`

Both created from scratch — neither existed before this sprint.

`docs/api.md` documents both endpoints' full request/response shapes, the `GENERAL` fallback, the low-confidence response shape, and the complete error-mapping table above — written by reading the actual controller/DTO/handler code, not from memory. One drafting error was caught and fixed in the process: unseeded country/visa-type reference data throws `IllegalStateException` inside `DocumentRepositoryAdapter`, which isn't a `VisaRagException` subtype, so it actually falls through to the generic `500` catch-all handler, not the `400` `VisaRagException` handler a first draft assumed.

`README.md` covers setup and run instructions (Docker Compose, choosing the OpenAI vs. Ollama profile), curl examples for both endpoints, the configuration property table, and test instructions (calling out that Docker must be running for the Testcontainers-backed tests).

**A known gap, flagged but not fixed this sprint:** `docs/ARCHITECTURE.md` is stale — written pre-implementation, before Sprint 4 changed how ingestion metadata actually flows (see `docs/knowledge_base_sourcing` context from that sprint). Also, `CLAUDE.md` itself says the file should be lowercase `docs/architecture.md`; the actual file on disk is `docs/ARCHITECTURE.md` — a pre-existing naming mismatch, not something this sprint introduced or was asked to fix. Out of scope for Sprint 6, which only covered `api.md`/`README.md`.

---

## Files Created / Changed in Sprint 6

```
src/main/java/com/neaz/travel/visa/presentation/controller/
  GlobalExceptionHandler.java                                   ← new
src/test/java/com/neaz/travel/visa/domain/service/
  ConfidenceScorerTest.java                                     ← 3 tests → 16
src/test/java/com/neaz/travel/visa/application/query/citation/
  CitationBuilderTest.java                                      ← new (6)
src/test/java/com/neaz/travel/visa/application/query/retrieval/
  HybridRetrieverTest.java                                      ← new (7)
src/test/java/com/neaz/travel/visa/application/query/prompt/
  VisaPromptTemplateTest.java                                   ← new (7)
src/test/java/com/neaz/travel/visa/
  RagPipelineIntegrationTest.java                                ← new (3, Testcontainers)
docs/
  api.md                                                         ← new
README.md                                                        ← new
```

---

## How to Verify Sprint 6 Works

```bash
docker version   # Testcontainers needs a running Docker daemon
mvn test
```

**Expected: `Tests run: 45, Failures: 0, Errors: 0`** — 39 unit tests plus the two Testcontainers integration test classes (`RagPipelineIntegrationTest`, and Sprint 3's pre-existing `DocumentRepositoryAdapterIntegrationTest`), all passing against real, ephemeral PostgreSQL and Qdrant containers. This is the first sprint where "all tests pass" actually includes an automated, real-store, end-to-end pass — not just `mvn compile` succeeding.
