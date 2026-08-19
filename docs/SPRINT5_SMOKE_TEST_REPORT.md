# Sprint 5 Smoke Test Report

| | |
|---|---|
| **Date** | 2026-08-19 |
| **Tester** | neazahsan-himel (with Claude Code) |
| **Scope** | End-to-end RAG pipeline: document ingestion → hybrid retrieval → LLM answer generation with citations |
| **Environment** | Local Windows 10 dev machine, Docker Desktop, local Ollama LLM (no external API dependency) |
| **Result** | **PASS** — both endpoints work end-to-end against a live stack |

---

## 1. Purpose

Sprint 5 (query + full RAG pipeline) had been marked done on the strength of `mvn compile` alone — every class compiled, but nothing had ever been executed against a running Postgres, Qdrant, or LLM. A smoke test is the cheapest possible check that the happy path isn't broken: boot the real stack, ingest a real document, ask a real question, and confirm a real answer with citations comes back. It is not a substitute for the unit/integration test suite planned for Sprint 6 — it only proves the system runs.

## 2. Environment Under Test

| Component | Configuration |
|---|---|
| App | Spring Boot 3.5.16, Java 21, run via `mvn spring-boot:run`, profile `ollama` |
| Postgres | `postgres:16` in Docker, host port **5433** (see §4.3) |
| Qdrant | `qdrant/qdrant:v1.9.2` in Docker, ports 6333/6334 |
| Chat model | Ollama `gemma4:latest` (local, CPU) |
| Embedding model | Ollama `nomic-embed-text` (768-dim) |
| App port | **8081** (see §4.6) |

LLM provider is configurable (OpenAI or Ollama) via `spring.ai.model.chat` / `spring.ai.model.embedding`, selected per Spring profile — this test exercised the **Ollama** path end-to-end, newly wired up as part of this test cycle (`spring-ai-starter-model-ollama`, `application-ollama.properties`).

## 3. Test Procedure

1. `docker compose up -d` — start Postgres + Qdrant, wait for both healthy.
2. `SPRING_PROFILES_ACTIVE=ollama mvn spring-boot:run` — boot the app; confirm Flyway migrations apply, Hibernate schema validation passes, Qdrant collection `visa_chunks` is created.
3. `POST /api/v1/ingestion/documents` — ingest three real knowledge-base documents (one `TOURIST`, two `GENERAL`).
4. `POST /api/v1/visa/query` — ask a real visa question scoped to `BD → IN, TOURIST`.
5. Inspect the response for a grounded answer, non-empty citations, and a confidence score above the 0.60 gate.

## 4. Issues Found and Fixed

None of the following were catchable by `mvn compile` or code review alone — each only surfaced when the pipeline was actually run. All are fixed and were required to reach a passing result.

| # | Area | Issue | Fix |
|---|---|---|---|
| 4.1 | `docker-compose.yml` | Qdrant healthcheck used `wget`, which the `qdrant/qdrant` image does not ship | Switched to a bash `/dev/tcp` TCP probe |
| 4.2 | Local Docker state | A `postgres_data` volume from an earlier session held stale credentials, so Postgres ignored the compose file's env vars | Wiped and recreated the volume (`docker compose down -v`), by explicit user approval |
| 4.3 | Local environment | A native Windows PostgreSQL service already owns host port 5432; the app was silently connecting to the wrong database | Remapped the Docker Postgres container to host port **5433**; updated `spring.datasource.url` accordingly |
| 4.4 | `V6/V7/V8__*.sql` | `SELECT id, code, name FROM countries CROSS JOIN (VALUES ...) AS v(code, name)` — `code`/`name` exist on both sides, ambiguous column reference, migration failed at boot | Qualified as `countries.id, v.code, v.name` in all three migrations |
| 4.5 | `V1/V3/V5__*.sql` | `iso_code`, `passport_iso`, `destination_iso` declared `CHAR(2)`, `file_hash` declared `CHAR(64)` — JPA entities map these fields as `VARCHAR` by default, so Hibernate's schema validation (`ddl-auto=validate`) rejected the mismatch at boot | Changed all four columns to `VARCHAR(n)` |
| 4.6 | Local environment | A local Docker Desktop Kubernetes cluster already owns host port 8080 | Moved the app to port **8081** via `server.port` |
| 4.7 | `QdrantCollectionInitializer` | Vector size was hardcoded to 1536 (OpenAI `text-embedding-3-small`'s dimension); failed with `INVALID_ARGUMENT: expected dim: 1536, got 768` once the embedding model switched to Ollama's `nomic-embed-text` | Made the dimension configurable (`rag.embedding.dimension`, default 1536), overridden to 768 in `application-ollama.properties` |

Items 4.2, 4.3, and 4.6 are local-machine environment conflicts, not application defects, but the fixes (port remapping, healthcheck) are now checked in so the stack boots cleanly on this machine going forward. Items 4.4, 4.5, and 4.7 are genuine, previously-uncaught application bugs.

## 5. Test Execution and Evidence

### 5.1 Ingestion

Three documents ingested successfully, each returning `HTTP 200` with a new `documentId` and `status: CREATED`:

| Document | Visa Type |
|---|---|
| India Tourist Visa – Required Documents (HCI Dhaka) | `TOURIST` |
| India Visa – Categories Overview | `GENERAL` |
| India Visa – Fee Policy | `GENERAL` |

### 5.2 Query

**Request:**
```
POST /api/v1/visa/query
{
  "query": "What documents do I need for an Indian tourist visa as a Bangladeshi passport holder?",
  "passport": "BD",
  "destination": "IN",
  "visaType": "TOURIST"
}
```

**Result:** `HTTP 200`

- **Answer:** a grounded, itemized list (passport validity, photo spec, proof of residence, proof of financial soundness, proof of profession) matching the ingested source document's actual content — no fabricated requirements.
- **Citations:** 3 entries, correctly mixing the `TOURIST`-specific document with both `GENERAL` documents — confirms the GENERAL-visa-type retrieval fallback (closed in Sprint 5, see `SPRINT5_EXPLAINED.md`) works at runtime on both the vector-search and full-text-search paths.
- **Confidence score:** `0.6263`, above the `0.60` gate in `rag.confidence.threshold` — the answer was returned rather than the "insufficient official information" fallback.

## 6. Verdict

**PASS.** The full RAG loop — ingestion, hybrid retrieval, GENERAL fallback, confidence gating, LLM generation, citation building — works end-to-end against a live stack, on the local Ollama provider path. This is the first time this pipeline has been executed since Sprint 5 was written; it had never previously run.

## 7. Known Limitations of This Test

- Only exercised the **Ollama** provider path, not OpenAI (OpenAI wiring was already present but untested here — no `OPENAI_API_KEY` was used in this session).
- Single query, single visa type, small knowledge-base corpus (3 documents). Does not exercise edge cases: empty results, multi-document conflicts, non-`TOURIST` visa types, or the low-confidence fallback path itself.
- No automated regression coverage — this was a manual, one-time run. Sprint 6 (unit + integration tests) is the follow-up that turns this into a repeatable, CI-enforced check.

## 8. Follow-Up for Sprint 6

- Unit tests: `ConfidenceScorer`, `CitationBuilder`, `HybridRetriever`, `VisaPromptTemplate`.
- Integration test: full query flow with Testcontainers, so this smoke test's coverage becomes automated and repeatable.
- `@RestControllerAdvice` global exception handler — the ingestion 500 hit during debugging (before the fixes in §4) returned a bare, unstructured error body; production error responses need a consistent `ApiResponse` shape.
- Explicitly test the low-confidence fallback path (`InsufficientContextException` → "I couldn't find sufficient official information.") — not exercised in this run since the test query scored above threshold.
