# Visa RAG Service

A production-grade Retrieval-Augmented Generation (RAG) service that answers visa questions using only official government and embassy documents — grounded, cited, and with an explicit no-hallucination fallback when the retrieved context isn't sufficient.

This is not a "chat with PDF" demo. It follows Clean Architecture, is backed by real hybrid retrieval (vector + full-text search, RRF-merged) over PostgreSQL and Qdrant, and is built as a senior-level Java backend portfolio project.

First supported route: **passport Bangladesh → destination India.**

**Status:** Phase 1 MVP complete — all 7 planned sprints shipped (ingestion, hybrid retrieval RAG pipeline, error handling/tests/docs, source-change monitoring & version supersession). Phase 2 (Visa Application Assistant) is underway — Phase B (Applicant Profile) and the core of Phase C (Passport Document Intelligence — MRZ + vision extraction, cross-field validation) are done; no REST API for Phase C yet. See [`docs/VISA_APPLICATION_ASSISTANT_PROPOSAL.md`](docs/VISA_APPLICATION_ASSISTANT_PROPOSAL.md) for the full roadmap. See [`SPRINTS.md`](SPRINTS.md) / [`PROGRESS.md`](PROGRESS.md) for the full task-level history.

---

## Tech Stack

| | |
|---|---|
| Backend | Java 21, Spring Boot 3.5.x, Spring AI, Maven |
| Database | PostgreSQL (documents, chunks, audit log — Flyway-managed) |
| Vector store | Qdrant |
| LLM | OpenAI (default) or Ollama (local, configurable) |
| Document processing | Apache Tika, PDFBox, JSoup |
| Testing | JUnit 5, Mockito, AssertJ, Testcontainers |
| Infra | Docker / Docker Compose |

---

## Architecture

Clean Architecture, strictly layered:

```
presentation → application → domain ← infrastructure
```

Business logic in `domain`/`application` has zero Spring/JPA/infrastructure dependencies. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full package map, dependency rules, and data-flow diagrams.

---

## Prerequisites

- Java 21
- Maven
- Docker Desktop (running) — for PostgreSQL, Qdrant, and (for tests) Testcontainers
- Either:
  - an OpenAI API key, **or**
  - a local [Ollama](https://ollama.com) install with `llava:7b` and `nomic-embed-text` pulled — `llava:7b` handles both RAG text queries and passport document-intelligence (§7 below) vision extraction. (An earlier `gemma4:latest` config was **verified NOT vision-capable** despite its own capability metadata claiming otherwise — see `PROGRESS.md`'s 2026-09-01/02 entries — and was replaced.)

---

## Setup & Run

### 1. Start PostgreSQL and Qdrant

```bash
docker compose up -d
```

This starts Postgres on host port `5433` (not `5432` — see the comment in `docker-compose.yml` if that surprises you on your machine) and Qdrant on `6333`/`6334`.

### 2. Choose an LLM provider

**OpenAI (default):**

```bash
export OPENAI_API_KEY=sk-...
mvn spring-boot:run
```

**Ollama (local, no API key, no network dependency):**

```bash
ollama pull llava:7b
ollama pull nomic-embed-text
SPRING_PROFILES_ACTIVE=ollama mvn spring-boot:run
```

The app boots on **port 8081** (`server.port` — 8080 is commonly claimed by other local tooling such as Docker Desktop's Kubernetes cluster). On startup, Flyway applies all migrations and the Qdrant collection `visa_chunks` is created if it doesn't already exist, sized to whichever embedding model's dimension is active (1536 for OpenAI, 768 for Ollama).

### 3. Ingest a document

```bash
curl -X POST http://localhost:8081/api/v1/ingestion/documents \
  -H "Content-Type: application/json" \
  -d '{
        "path": "India/Tourist/hci-dhaka-visa-documents.md",
        "title": "India Tourist Visa - Required Documents (HCI Dhaka)",
        "metadata": {
          "destinationIso": "IN",
          "passportIso": "BD",
          "visaTypeCode": "TOURIST",
          "lastUpdated": "2026-01-01"
        }
      }'
```

`path` is resolved against `knowledge-base/` (see `knowledge-base.root`), which already contains curated content sourced from HCI Dhaka's official visa documents.

### 4. Ask a question

```bash
curl -X POST http://localhost:8081/api/v1/visa/query \
  -H "Content-Type: application/json" \
  -d '{
        "query": "What documents do I need for an Indian tourist visa as a Bangladeshi passport holder?",
        "passport": "BD",
        "destination": "IN",
        "visaType": "TOURIST"
      }'
```

Full request/response shapes, including the error format and the low-confidence fallback response, are documented in [`docs/api.md`](docs/api.md).

### 5. Review flagged source changes

A scheduled job checks every active document's official source for changes and flags anything that differs. See what's pending review:

```bash
curl http://localhost:8081/api/v1/ingestion/pending-changes
```

Re-ingest a flagged document via step 3's endpoint — this automatically deactivates the prior version in both PostgreSQL and Qdrant. Full workflow in [`docs/api.md`](docs/api.md#source-change-monitoring).

### 6. Build an applicant profile

`applicantId` is any UUID you choose — there's no login yet (see [Applicant Identity](docs/api.md#applicant-identity)):

```bash
APPLICANT_ID=$(uuidgen)

curl -X POST http://localhost:8081/api/v1/applicants/$APPLICANT_ID/profile/fields \
  -H "Content-Type: application/json" \
  -d '{ "fieldPath": "personalInfo.fullName", "value": "Jane Doe" }'

curl http://localhost:8081/api/v1/applicants/$APPLICANT_ID/profile
```

Every field set this way is self-declared and starts already-confirmed. Full field list, the address-object shape, and the confirm/correct/reject verification endpoint are documented in [`docs/api.md`](docs/api.md).

### 7. Passport document intelligence (production flow has no REST endpoint yet)

The pipeline that turns an uploaded passport image into reviewable `ApplicantProfile` field candidates — image quality gate, MRZ OCR + checksum validation, vision-model extraction of fields the MRZ doesn't carry (issue date, place of birth, passport type), cross-checking the fields both sources supply, and never auto-trusting anything until the applicant confirms it — is implemented (`application/document/DocumentIntelligenceService`, `ExtractedFieldConfirmationService`) but **has no controller wired up yet**, and needs Tesseract OCR configured on the host before it can (it runs MRZ extraction before vision and rejects the upload if that fails). See [`PROGRESS.md`](PROGRESS.md#phase-c--passport-document-intelligence-core-done-2026-09-01) for what exists and [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the flow.

Under the `ollama` profile, the vision-extraction step runs against `llava:7b` — verified live (not assumed), both through a JUnit test against a real local Ollama instance and over real HTTP through the running app (below): field accuracy varies between runs (5/6 correct one run; two fields swapped onto each other's keys another run) — a real, run-to-run OCR-accuracy limitation, not the "model never consumed the image at all" failure `gemma4:latest` had. See `PROGRESS.md`'s 2026-09-02 entry for full results and remaining limitations.

**Vision preview (diagnostic endpoint, works today):** calls the vision-extraction step directly over HTTP, bypassing OCR/MRZ/persistence, so you can test it against a real image without Tesseract set up:

```bash
curl -X POST http://localhost:8081/api/v1/applicants/$(uuidgen)/documents/passport/vision-preview \
  -F "file=@/path/to/your/passport-image.jpg"
```

Not the production upload flow — see `docs/api.md`'s Document Intelligence section for the response shape and caveats.

---

## Configuration

Key properties (`application.properties`; see `application-ollama.properties` for the Ollama profile overrides):

| Property | Default | Meaning |
|---|---|---|
| `server.port` | `8081` | HTTP port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5433/visa_rag` | Postgres connection |
| `qdrant.host` / `qdrant.port` | `localhost` / `6334` | Qdrant gRPC endpoint |
| `spring.ai.model.chat` / `spring.ai.model.embedding` | `openai` | Provider selection; `ollama` under the `ollama` profile |
| `rag.chunking.size` / `rag.chunking.overlap` | `512` / `64` | Sentence-aware sliding-window chunking, in approximate tokens |
| `rag.retrieval.top-k` | `20` | Candidates pulled per retrieval path before RRF fusion |
| `rag.confidence.threshold` | `0.60` | Below this, the query endpoint returns the no-hallucination fallback instead of calling the LLM |
| `rag.embedding.dimension` | `1536` (`768` for Ollama) | Must match the active embedding model's output size |
| `knowledge-base.root` | `knowledge-base` | Root directory ingestion paths resolve against |
| `source-monitoring.fetch-timeout-seconds` | `10` | HTTP timeout before a source-change check is treated as unreachable |
| `source-monitoring.check-interval-ms` | `86400000` (24h) | Delay between scheduled source-change monitor runs |

All can be overridden via environment variables (Spring's relaxed binding), e.g. `SPRING_DATASOURCE_URL`, `QDRANT_HOST`.

---

## Running Tests

```bash
mvn test
```

Docker must be running — several tests use [Testcontainers](https://testcontainers.com) to spin up real, ephemeral PostgreSQL and Qdrant instances rather than mocking persistence and vector search. These are tagged `@Tag("integration")`:

- `DocumentRepositoryAdapterIntegrationTest` — persistence layer against real Postgres
- `RagPipelineIntegrationTest` — the full ingest-then-query pipeline (real Postgres + real Qdrant; only the embedding/chat models are stubbed, since a real LLM call would make the suite network-dependent and non-deterministic) — covers the happy path, the `GENERAL` visa-type fallback, and the low-confidence fallback response
- `ApplicantProfileRepositoryAdapterIntegrationTest` — round-trips every applicant-profile field type through real Postgres, and confirms the save strategy replaces fields rather than accumulating duplicate rows

Everything else is a fast, in-process unit test (domain services, application orchestration with mocked ports, prompt/citation rendering).

The `docs/SPRINT5_SMOKE_TEST_REPORT.md` describes the one manual, full-stack run this automated suite is built to replace going forward.

---

## Project Structure

```
com.neaz.travel.visa
├── config/            Spring @Configuration only, no business logic
├── common/            exceptions, ApiResponse envelope, stateless utils (e.g. Sha256Hasher) — no dependency on any other local package
├── presentation/      controllers, DTOs, mappers, GlobalExceptionHandler
├── application/       use-case orchestration, ports (interfaces infrastructure implements)
├── domain/            pure Java — no Spring, no JPA
└── infrastructure/    persistence, vectorstore, embedding, llm, document adapters
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for full package responsibilities and the dependency-rule table.

---

## Documentation

- [`docs/api.md`](docs/api.md) — endpoint reference: request/response shapes, error format, the `GENERAL` fallback, the low-confidence response, source-change monitoring
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — package structure, dependency rules, data flow, RAG pipeline detail, database schema
- [`docs/SPRINT5_EXPLAINED.md`](docs/SPRINT5_EXPLAINED.md) — how the query/retrieval pipeline works, in narrative form
- [`docs/SPRINT5_SMOKE_TEST_REPORT.md`](docs/SPRINT5_SMOKE_TEST_REPORT.md) — the first live end-to-end run and what it caught
- [`docs/SPRINT6_EXPLAINED.md`](docs/SPRINT6_EXPLAINED.md) — global exception handling, the test suite, and the docs written to close out Sprint 6
- [`docs/SPRINT7_EXPLAINED.md`](docs/SPRINT7_EXPLAINED.md) — source-change monitoring and document version supersession, in narrative form
- [`docs/VISA_APPLICATION_ASSISTANT_PROPOSAL.md`](docs/VISA_APPLICATION_ASSISTANT_PROPOSAL.md) — architecture proposal for Phase 2 (Applicant Profile → Document Intelligence → Form Preparation → Web Form Assistant), approved 2026-08-31; Phase B (Applicant Profile) and the core of Phase C (Passport Document Intelligence) are shipped, the rest is phase-gated
- [`PROGRESS.md`](PROGRESS.md#phase-c--passport-document-intelligence-core-done-2026-09-01) — Phase C (Passport Document Intelligence): MRZ + vision extraction, cross-field validation, confidence heuristics, the `gemma4:latest` vision-capability finding, and the `llava:7b` swap + live verification
- [`CLAUDE.md`](CLAUDE.md) / [`DEVELOPMENT_RULES.md`](DEVELOPMENT_RULES.md) — project governance: architecture rules, coding principles, and process this codebase is built to
