# Architecture — Visa RAG Service

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Package Structure](#2-package-structure)
3. [Package Responsibilities](#3-package-responsibilities)
4. [Dependency Rules](#4-dependency-rules)
5. [Data Flow](#5-data-flow)
6. [RAG Pipeline](#6-rag-pipeline)
7. [Database Schema](#7-database-schema)
8. [Future Roadmap](#8-future-roadmap)

---

## 1. System Overview

The Visa RAG Service is a **Retrieval-Augmented Generation** system that answers visa-related queries using only content extracted from official government and embassy documents. It is designed as a production-grade backend service, not a prototype.

### Core Design Goals

| Goal | Decision |
|---|---|
| Accuracy | Every answer is grounded in retrieved source chunks; no hallucination |
| Maintainability | Strict Clean Architecture — layers never violate the dependency rule |
| Scalability | Stateless HTTP service; external stores (PostgreSQL, Qdrant) scale independently |
| Auditability | All ingested documents, queries, and retrieved chunks are logged to PostgreSQL |
| Extensibility | New passport/destination pairs require only new documents and metadata, not new code |

### High-Level Component Map

```
┌──────────────────────────────────────────────────────────────┐
│                       Client (REST)                          │
└──────────────────────────┬───────────────────────────────────┘
                           │ HTTP
┌──────────────────────────▼───────────────────────────────────┐
│                    Presentation Layer                        │
│  VisaQueryController  IngestionController  ApplicantProfileController │
└──────────┬───────────────────────────────┬───────────────────┘
           │                               │
┌──────────▼───────────────────────────────▼───────────────────┐
│                    Application Layer                         │
│                                                              │
│  query/                       ingestion/                     │
│    VisaQueryService              DocumentIngestionService    │
│    retrieval/                 monitoring/                    │
│      HybridRetriever            SourceChangeMonitor          │
│      ReRanker                   PendingChangeQueryService    │
│    prompt/                    applicant/                     │
│      VisaPromptTemplate         ApplicantProfileService      │
│    citation/                    ApplicantProfileVerification-│
│      CitationBuilder              Service                    │
│                                  ApplicantFieldValueParser   │
│                                document/                      │
│                                  DocumentIntelligenceService │
│                                  ExtractedFieldConfirmation-  │
│                                    Service                    │
│                                port/                          │
│                                  EmbeddingPort               │
│                                  VectorSearchPort            │
│                                  ChatPort                    │
│                                  ImageQualityPort  OcrPort   │
│                                  VisionExtractionPort         │
│                                  DocumentStoragePort          │
└──────────┬───────────────────────────────┬───────────────────┘
           │                               │
┌──────────▼───────────────────────────────▼───────────────────┐
│                       Domain Layer                           │
│   model/  VisaQuery  VisaAnswer  Document  Chunk             │
│           RetrievedContext  DocumentMetadata  SourceCheckLog │
│           ApplicantProfile  ExtractedField  VerificationStatus│
│           PersonalInfo/PassportInfo/.../TravelInfo  Address  │
│           ApplicantDocument  DocumentType                    │
│           DocumentProcessingStatus  MrzData                  │
│           ImageQualityAssessment  ImageQualityIssue          │
│           VisualPassportData  VisualFieldReading             │
│   repository/  DocumentRepository  VisaQueryLogRepository   │
│                ApplicantProfileRepository                    │
│                ApplicantDocumentRepository                   │
│   service/     ConfidenceScorer  MrzParser                  │
│                PassportFieldValidator                        │
└──────────────────────────────────────────────────────────────┘
                           ▲
                           │ implements
┌──────────────────────────┴───────────────────────────────────┐
│                   Infrastructure Layer                       │
│                                                              │
│  persistence/   JPA entities, Spring Data repos, adapters   │
│                 (incl. ApplicantProfileRepositoryAdapter,    │
│                 ApplicantDocumentRepositoryAdapter)           │
│  vectorstore/   QdrantVectorStoreAdapter                    │
│  embedding/     EmbeddingAdapter                            │
│  llm/           ChatAdapter                                 │
│  document/      TikaDocumentParser, ChunkingStrategy,       │
│                 FileSystemDocumentLoader, UrlDocumentLoader, │
│                 MetadataExtractor,                            │
│                 quality/ImageQualityAnalyzer,                │
│                 ocr/TesseractOcrAdapter,                     │
│                 vision/VisionPassportExtractionAdapter        │
└──────────────────────────────────────────────────────────────┘
```

> `application/monitoring/` and the Applicant Profile additions (`application/applicant/`, `ApplicantProfile` and its view types, `ApplicantProfileRepository`, `ApplicantProfileRepositoryAdapter`) are not in this diagram's original Phase 1 design — see [§8 Future Roadmap](#8-future-roadmap) and `docs/VISA_APPLICATION_ASSISTANT_PROPOSAL.md` for why each was added and when. Same for `application/document/` and the `ImageQualityPort`/`OcrPort`/`VisionExtractionPort`/`DocumentStoragePort` ports (Phase C, §5.4 below) — the original proposal sketched a different port set (`OcrPort`/`DocumentClassificationPort`/`FieldExtractionPort`/`DocumentValidationPort`); see `PROGRESS.md`'s Phase C section for why it changed.

---

## 2. Package Structure

Root: `com.neaz.travel.visa`

```
com.neaz.travel.visa
├── config/
├── common/
│   ├── exception/
│   ├── model/
│   └── util/
├── presentation/
│   ├── controller/
│   ├── dto/
│   └── mapper/
├── application/
│   ├── query/
│   │   ├── retrieval/
│   │   ├── prompt/
│   │   └── citation/
│   ├── ingestion/
│   ├── monitoring/
│   ├── applicant/
│   ├── document/
│   ├── command/
│   ├── result/
│   └── port/
├── domain/
│   ├── model/
│   ├── repository/
│   └── service/
└── infrastructure/
    ├── persistence/
    ├── vectorstore/
    ├── embedding/
    ├── llm/
    └── document/
        ├── ocr/
        ├── quality/
        └── vision/
```

No package may be created outside this structure without an explicit architectural decision. `application/monitoring/` (Sprint 7), `application/applicant/` (Phase B), and `application/document/` (Phase C) were all added this way, via `AskUserQuestion`, before being created — see §8 and the relevant `docs/SPRINT*_EXPLAINED.md` / `docs/VISA_APPLICATION_ASSISTANT_PROPOSAL.md`. `infrastructure/document/{ocr,quality,vision}` are sub-packages of the pre-existing `infrastructure/document/`, not new top-level packages.

---

## 3. Package Responsibilities

### `config`

Spring `@Configuration` classes only. No business logic.

- `VectorStoreConfig` — Qdrant client bean
- `EmbeddingModelConfig` — OpenAI / Ollama embedding model bean
- `ChatModelConfig` — OpenAI / Ollama chat model bean
- `WebConfig` — CORS and serialization settings

### `common`

Shared value types, exceptions, and utilities used across all layers. Has no dependency on any other local package.

- `exception/` — `VisaRagException`, `DocumentNotFoundException`, `InsufficientContextException`, `ApplicantProfileNotFoundException`, `ApplicantDocumentNotFoundException`
- `model/` — `PagedResult<T>`, `ApiResponse<T>`
- `util/` — `TextSanitizer`, `MetadataValidator`, `Sha256Hasher`

### `presentation`

Spring MVC controllers, DTOs, request/response mappers, and the global exception handler. Depends only on `application` commands and results.

- `controller/VisaQueryController` — `POST /api/v1/visa/query`
- `controller/IngestionController` — `POST /api/v1/ingestion/documents`, `GET /api/v1/ingestion/pending-changes`
- `controller/ApplicantProfileController` — `POST/GET /api/v1/applicants/{applicantId}/profile[/fields[/verify]]`
- `controller/GlobalExceptionHandler` — `@RestControllerAdvice`; translates every exception into the shared `ApiResponse` envelope
- `dto/` — `VisaQueryRequest`, `VisaQueryResponse`, `IngestionRequest`, `IngestionResponse`, `SetApplicantFieldRequest`, `VerifyApplicantFieldRequest`, `ApplicantProfileResponse`
- `mapper/` — maps between DTOs and application-layer commands/results (`VisaQueryMapper`, `IngestionMapper`, `ApplicantProfileMapper`)

> **No controller for Phase C (Passport Document Intelligence) yet.** `application/document/DocumentIntelligenceService` and `ExtractedFieldConfirmationService` (§3.1 below) have no `presentation` entry point — `UploadApplicantDocumentCommand`/`ConfirmExtractedFieldsCommand` must be constructed directly. See `PROGRESS.md`'s Phase C section.

### `application`

Orchestrates use cases. Depends on `domain` interfaces and defines outbound ports that `infrastructure` implements. Never imports from `infrastructure` or `presentation`.

#### `application/query`

The visa query use case and all components that serve it.

- `VisaQueryService` — receives a `VisaQueryCommand`, drives hybrid retrieval, calls the LLM, and returns a `VisaQueryResult`
- `retrieval/HybridRetriever` — issues a pre-filtered vector search and a pre-filtered full-text search in parallel, then merges results via Reciprocal Rank Fusion
- `retrieval/ReRanker` — scores and orders the merged chunk list by relevance
- `prompt/VisaPromptTemplate` — constructs the final LLM prompt: context chunks, citations, constraints, and the user question
- `citation/CitationBuilder` — maps chunk metadata to structured `Citation` objects for the response

#### `application/ingestion`

The document ingestion use case.

- `DocumentIngestionService` — receives an `IngestDocumentCommand` and delegates to infrastructure for parsing, chunking, embedding, and persistence; the service itself contains no parsing or I/O logic; also handles version supersession (deactivating the prior active document with the same `documentKey` when re-ingesting changed content — keyed by document identity, not `sourceUrl`, because one official source can yield several documents)

#### `application/monitoring`

Source-change detection (Sprint 7) — approved as a new sub-package via `AskUserQuestion` since it wasn't in the original package list.

- `SourceChangeMonitor` — `@Scheduled` job; fetches each active document's `sourceUrl`, hashes it the same way ingestion does, and logs `UNCHANGED`/`CHANGED`/`FETCH_FAILED` to `SourceCheckLogRepository`; never writes to the knowledge base itself
- `PendingChangeQueryService` — read side for `GET /api/v1/ingestion/pending-changes`

#### `application/applicant`

The applicant-profile use cases (Phase B) — approved as a new sub-package the same way `application/monitoring` was.

- `ApplicantProfileService` — finds or lazily creates an applicant's profile and sets one field, converting the submitted value into the field's declared type
- `ApplicantProfileVerificationService` — applies a confirm/correct/reject decision to one field; the only place a field's `VerificationStatus` changes
- `ApplicantFieldValueParser` — public (a second caller, `application/document/ExtractedFieldConfirmationService`, needed it outside `application/applicant`); the single field-path-to-Java-type mapping every caller shares, so it can't drift between them
- `ApplicationService` — creates and withdraws an `Application` (Phase D). `withdraw`/`getOwned` enforce the same ownership-filter pattern as `ExtractedFieldConfirmationService` (`findById` filtered by `applicantId`, else `ApplicationNotFoundException`)
- `ChecklistService` — answers "what documents does this application need" by delegating to `VisaQueryService` with a `MetadataFilter` built from the application's own passport/destination/visa-type — no separately maintained document-list data exists; the existing knowledge base already answers this. Catches `InsufficientContextException` itself and converts it into a `ChecklistResult` with the fixed fallback text, since `GlobalExceptionHandler`'s existing handler for that exception returns a `VisaQueryResponse`-shaped body, which doesn't match `ChecklistResponse`
- `ConsistencyValidationService` — scans an applicant's profile field map for unresolved `CONFLICT_SUFFIX_MRZ`/`CONFLICT_SUFFIX_VISUAL` key pairs left by `DocumentIntelligenceService`. **Scope deviation from the original proposal** (confirmed via `AskUserQuestion`): the proposal's `docs/VISA_APPLICATION_ASSISTANT_PROPOSAL.md` describes this as a cross-*document*-type check (e.g. passport vs. NID), but `DocumentType` only has `PASSPORT` today — there is no second document type to compare against yet. Reusing the existing MRZ-vs-visual conflict posture is the one genuinely implementable form of "surface conflicts, don't silently resolve them" available right now

#### `application/document`

Passport document intelligence (Phase C) — approved as a new sub-package the same way `application/monitoring` and `application/applicant` were. Implements the safety principle *AI extracts → system validates → applicant verifies → only then trusted downstream*: nothing this package returns is written into an `ApplicantProfile` except through `ExtractedFieldConfirmationService`, and every extracted candidate starts `VerificationStatus.UNVERIFIED` regardless of confidence.

- `DocumentIntelligenceService` — the upload-to-candidates pipeline: stores the image, runs the image-quality gate, then reads fields from two independent sources — MRZ OCR (checksum-validated, via `MrzParser`) and vision-model extraction (validated via `PassportFieldValidator`, self-reported readability) — and cross-checks the fields both sources supply (passport number, DOB, expiry). Agreement keeps the MRZ candidate; disagreement writes neither under the plain `ApplicantProfile.PATH_*` key — both surface instead under `path + ".conflict.mrz"` / `".conflict.visual"` so a conflict is never silently resolved. See §5.4.
- `ExtractedFieldConfirmationService` — the only path by which a document-derived candidate ever reaches an `ApplicantProfile`: loads the source `ApplicantDocument`, writes each field the applicant accepted as `USER_CONFIRMED` with `confidence = 1.0` (superseding whatever MRZ/visual confidence it carried), and marks the document confirmed

#### `application/command`

Immutable input objects passed from `presentation` to `application`.

- `VisaQueryCommand` — query text, passport ISO, destination ISO, optional visa type code
- `IngestDocumentCommand` — document path or URL, metadata fields
- `SetApplicantFieldCommand` — applicant id, field path, submitted value
- `VerifyApplicantFieldCommand` — applicant id, field path, action (`CONFIRM`/`CORRECT`/`REJECT`), optional corrected value
- `UploadApplicantDocumentCommand` — applicant id, `DocumentType`, raw image bytes (defensively copied)
- `ConfirmExtractedFieldsCommand` — applicant id, document id, accepted field values (only what the applicant chose to keep)
- `CreateApplicationCommand` — applicant id, passport ISO, destination ISO, visa type code

#### `application/result`

Immutable output objects returned from `application` to `presentation`.

- `VisaQueryResult` — answer text, citations, confidence score, model ID, latency
- `IngestionResult` — document ID, chunk count, status
- `ApplicantProfileResult` — the 7 grouped `*Info` views plus id/applicantId/lastModifiedAt; static factory `from(ApplicantProfile)`
- `DocumentExtractionResult` — one of `QUALITY_REJECTED` (a one-sentence `message` plus the specific `Set<ImageQualityIssue>` that caused it, so the client can render an actionable per-issue checklist rather than just the sentence) or `EXTRACTED` (field candidates keyed by `ApplicantProfile.PATH_*`, except conflicted fields — see `path + ".conflict.mrz"`/`".conflict.visual"` above)
- `ApplicationResult` — mirrors `Application`; static factory `from(Application)`
- `ChecklistResult` — application id, generated checklist text, citations, confidence score (same shape/guarantees as `VisaQueryResult`)
- `ConsistencyCheckResult` — applicant id, `consistent` flag, list of `FieldConflict` (field path + the two disagreeing `ExtractedField` readings)

#### `application/port`

Outbound port interfaces defined by the application layer and implemented by infrastructure.

- `EmbeddingPort` — `embed(text: String): float[]`
- `VectorSearchPort` — `search(query: float[], filter: MetadataFilter, topK: int): List<ScoredChunk>`
- `ChatPort` — `generate(prompt: String, modelId: String): String`
- `DocumentParsingPort` — `parse(source: DocumentSource): String`
- `DocumentChunkingPort` — `chunk(text: String, metadata: DocumentMetadata): List<Chunk>`
- `ImageQualityPort` — `assess(imageBytes: byte[]): ImageQualityAssessment` — pre-OCR blur/lighting/glare/skew/MRZ-readability gate
- `OcrPort` — `extractText(imageBytes: byte[]): String` — whole-image OCR; `MrzParser` locates the MRZ lines within the result
- `VisionExtractionPort` — `extract(imageBytes: byte[]): VisualPassportData` — reads visual-page fields via a multimodal chat model
- `DocumentStoragePort` — `store(applicantId: UUID, documentId: UUID, imageBytes: byte[]): String` — persists raw uploaded bytes, returns a storage reference

### `domain`

Pure Java. Zero Spring, JPA, or infrastructure dependencies. No framework annotations.

#### `domain/model`

- `VisaQuery` — record; parsed query with passport/destination/visa type context
- `VisaAnswer` — record; answer text, citations, confidence score
- `Document` — aggregate root; document metadata and its associated chunk list
- `Chunk` — entity; a text segment, its token count, and its Qdrant point ID
- `RetrievedContext` — record; list of `ScoredChunk` results from hybrid retrieval, carrying plain similarity scores (no infrastructure types)
- `DocumentMetadata` — record; country, visaType, passport, source URL, lastUpdated, version
- `ScoredChunk` — record; a `Chunk` with a `double fusionScore` from RRF
- `MetadataFilter` — record; passport ISO, destination ISO, optional visa type code — passed to both retrieval paths before scoring
- `SourceCheckLog` — record; one source-change-monitor check result (`UNCHANGED`/`CHANGED`/`FETCH_FAILED`)
- `ApplicantProfile` — aggregate root (Phase B); a generic `Map<String, ExtractedField<?>>` keyed by canonical dot-paths, not nested storage — `confirmField`/`correctField`/`rejectField` operate uniformly on any path this way. `PersonalInfo`, `PassportInfo`, `NationalityInfo`, `AddressInfo`, `FamilyInfo`, `EmploymentInfo`, `TravelInfo` are read-only views assembled from the map on demand, not the actual storage
- `ExtractedField<T>` — record; provenance wrapper (extracted value, normalized value, confidence, source document id, `VerificationStatus`) around every profile field value except `purposeOfVisit`
- `VerificationStatus` — enum; `UNVERIFIED`, `USER_CONFIRMED`, `USER_CORRECTED`, `REJECTED`
- `Address` — record; the composite value type inside `AddressInfo`
- `ApplicantDocument` — entity (Phase C); an uploaded document moving through `UPLOADED` → `QUALITY_REJECTED` / `EXTRACTED` → `CONFIRMED`
- `DocumentType` — enum; only `PASSPORT` exists so far
- `DocumentProcessingStatus` — enum; `ApplicantDocument`'s lifecycle states, above
- `ImageQualityAssessment` / `ImageQualityIssue` — record / enum; pre-OCR quality-gate outcome and specific failure reasons (`BLURRY`, `SKEWED`, `POOR_LIGHTING`, `GLARE_OR_SHADOW`, `MRZ_NOT_READABLE`)
- `MrzData` — record; TD3 machine-readable-zone fields plus per-field and composite ICAO 9303 check-digit validity; a failed checksum doesn't discard the value, it's still returned for the caller to weigh
- `VisualPassportData` / `VisualFieldReading` — record / record; fields read off the visual (non-MRZ) passport page by the vision model, each carrying the model's self-reported per-field readability instead of a checksum
- `Application` — entity (Phase D); the passport/destination/visa-type scope an applicant is working against, same shape as `MetadataFilter`. Lifecycle: `OPEN` → `WITHDRAWN` via `withdraw()`; no "completed" state yet — that belongs to form preparation (Phase E)
- `ApplicationStatus` — enum; `OPEN`, `WITHDRAWN`

#### `domain/repository`

Interface contracts implemented in `infrastructure/persistence`.

- `DocumentRepository` — CRUD for `Document` aggregates and `Chunk` entities
- `VisaQueryLogRepository` — append-only audit log writes
- `SourceCheckLogRepository` — append-only source-check-result writes/reads
- `ApplicantProfileRepository` — CRUD for `ApplicantProfile` aggregates; one profile per applicant
- `ApplicantDocumentRepository` — CRUD for `ApplicantDocument` entities
- `ApplicationRepository` — `save`/`findById` for `Application` entities

#### `domain/service`

- `ConfidenceScorer` — domain service; computes a `[0.0, 1.0]` score from a `RetrievedContext` using mean fusion score, chunk count, keyword match ratio, and document recency; operates only on plain Java types
- `MrzParser` — locates and parses a TD3 machine-readable zone out of raw OCR text; validates every ICAO 9303 check digit (per-field plus composite)
- `PassportFieldValidator` — format/plausibility checks for visual-page fields (date ranges, passport-number format, passport-type label, place-of-birth length); has no equivalent for MRZ fields, whose trust signal is the check digit instead

### `infrastructure`

Implements domain repository interfaces and application port interfaces. May import Spring, JPA, Qdrant client, Spring AI, and all third-party libraries. Must never be imported by `domain` or `application`.

#### `infrastructure/persistence`

- JPA `@Entity` classes (`DocumentJpaEntity`, `ChunkJpaEntity`, `QueryAuditJpaEntity`, `CountryJpaEntity`, `VisaTypeJpaEntity`, `SourceCheckLogJpaEntity`, `ApplicantProfileJpaEntity`, `ExtractedFieldJpaEntity`, `ApplicantDocumentJpaEntity`, `ApplicationJpaEntity`)
- Spring Data `JpaRepository` interfaces
- Adapter classes that implement the domain repositories by delegating to the Spring Data repositories, including `ApplicantProfileRepositoryAdapter` — persists `ApplicantProfile`'s field map as one `ExtractedFieldJpaEntity` row per field (relational, not JSON), replacing all rows on every save rather than diffing individual changes — and `ApplicantDocumentRepositoryAdapter`/`ApplicationRepositoryAdapter`
- Flyway migration SQL files live in `src/main/resources/db/migration/`

#### `infrastructure/vectorstore`

- `QdrantVectorStoreAdapter` — implements `VectorSearchPort`; applies a Qdrant payload filter (passport, destination, visa type) **before** issuing the cosine similarity search; never performs an unfiltered query

#### `infrastructure/embedding`

- `EmbeddingAdapter` — implements `EmbeddingPort`; delegates to Spring AI `EmbeddingModel`

#### `infrastructure/llm`

- `ChatAdapter` — implements `ChatPort`; delegates to Spring AI `ChatModel`; configurable for OpenAI or Ollama

#### `infrastructure/document`

- `TikaDocumentParser` — implements `DocumentParsingPort`; extracts plain text from PDF, DOCX, HTML via Apache Tika
- `ChunkingStrategy` — implements `DocumentChunkingPort`; sliding-window chunker with sentence-boundary awareness producing `Chunk` domain objects
- `FileSystemDocumentLoader` — reads raw bytes from `knowledge-base/` on the local filesystem
- `UrlDocumentLoader` — fetches official documents from configured URLs
- `MetadataExtractor` — reads sidecar JSON frontmatter and constructs `DocumentMetadata`
- `FileSystemApplicantDocumentStore` — implements `DocumentStoragePort`; writes uploaded passport images under `applicant-documents.storage-root`
- `quality/ImageQualityAnalyzer` — implements `ImageQualityPort`; plain-Java (no OpenCV/native CV library) pixel-level heuristics — Laplacian-variance blur, mean-brightness lighting, saturated-pixel glare/shadow, projection-profile skew, MRZ-band edge-density readability
- `ocr/TesseractOcrAdapter` — implements `OcrPort`; self-hosted Tesseract via Tess4J, whole-image OCR (no region cropping)
- `vision/VisionPassportExtractionAdapter` — implements `VisionExtractionPort`; reuses the same Spring AI `ChatModel` bean `ChatAdapter` uses for RAG answers (no separate provider/API key) with a fixed JSON-contract prompt asking for the six visual-page fields; parses defensively (strips markdown code fences, degrades to `VisualPassportData.empty()` on any parse failure or model-call exception, never throws) — see §5.4 for why this matters under the `ollama` profile specifically

---

## 4. Dependency Rules

```
presentation  →  application  →  domain  ←  infrastructure
```

| Layer | May import | Must never import |
|---|---|---|
| `domain` | `common`, plain Java | `application`, `infrastructure`, `presentation`, any Spring class |
| `application` | `domain`, `common` | `infrastructure`, `presentation`, Spring (except `@Service` / `@Component` for wiring) |
| `infrastructure` | `domain`, `application/port`, `common`, Spring, third-party libs | `presentation` |
| `presentation` | `application/command`, `application/result`, `common`, Spring MVC | `domain` internals, `infrastructure` |
| `config` | Spring, `infrastructure` beans | (wiring only; no business logic) |
| `common` | plain Java | all local packages |

Violating the dependency rule is a defect, not a shortcut.

---

## 5. Data Flow

### 5.1 Query Flow

```
Client
  │  POST /api/v1/visa/query
  │  { "query": "...", "passport": "BD", "destination": "IN", "visaType": "TOURIST" }
  ▼
VisaQueryController
  │  validate request → build VisaQueryCommand
  ▼
VisaQueryService (application/query)
  │  1. parse command into VisaQuery domain object
  │  2. build MetadataFilter { passport="BD", destination="IN", visaType="TOURIST" }
  │  3. generate query embedding via EmbeddingPort
  │  4. call HybridRetriever(queryEmbedding, metadataFilter)
  │       │
  │       ├─ QdrantVectorStoreAdapter.search(embedding, filter, topK=20)
  │       │    └─ filter applied as Qdrant payload filter BEFORE cosine ranking
  │       │
  │       └─ DocumentRepository.fullTextSearch(queryText, filter, topK=20)
  │            └─ filter applied as PostgreSQL WHERE clause BEFORE ts_rank scoring
  │       │
  │       └─ Reciprocal Rank Fusion → merged list of ScoredChunk
  │
  │  5. ReRanker.rerank(mergedChunks) → ordered RetrievedContext
  │  6. ConfidenceScorer.score(context) → double
  │     if score < threshold → return InsufficientContextException → 200 with fallback message
  │  7. VisaPromptTemplate.build(context, userQuery) → String prompt
  │  8. ChatPort.generate(prompt, modelId) → String rawAnswer
  │  9. CitationBuilder.build(context) → List<Citation>
  │  10. QueryAuditLogger persists query, chunk IDs, answer, score, latency
  │  11. assemble VisaQueryResult
  ▼
VisaQueryController
  │  map result → VisaQueryResponse DTO
  ▼
Client  { "answer": "...", "citations": [...], "confidence": 0.91 }
```

### 5.2 Ingestion Flow

```
Admin
  │  POST /api/v1/ingestion/documents  { "path": "...", "metadata": {...} }
  ▼
IngestionController → build IngestDocumentCommand
  ▼
DocumentIngestionService (application/ingestion)
  │  1. FileSystemDocumentLoader (or UrlDocumentLoader) → raw bytes
  │  2. DocumentParsingPort → plain text
  │  3. MetadataExtractor → DocumentMetadata
  │  4. DocumentChunkingPort → List<Chunk>
  │  5. EmbeddingPort → float[] per chunk
  │  6. DocumentRepository.save(document + chunks) → persists to PostgreSQL
  │  7. VectorSearchPort.upsert(chunkId, vector, metadata) → upserts to Qdrant
  │  8. return IngestionResult
  ▼
IngestionController → IngestionResponse DTO → Client
```

### 5.3 Applicant Profile Flow (Phase B)

```
Client
  │  POST /api/v1/applicants/{applicantId}/profile/fields  { "fieldPath": "...", "value": ... }
  ▼
ApplicantProfileController → build SetApplicantFieldCommand
  ▼
ApplicantProfileService (application/applicant)
  │  1. ApplicantProfileRepository.findByApplicantId(applicantId) → existing profile, or create a new one
  │  2. ApplicantFieldValueParser.parseTypedValue(fieldPath, value) → typed Java value
  │  3. ApplicantProfile.setField(fieldPath, ExtractedField.selfDeclared(value))  [or setPurposeOfVisit for that one path]
  │  4. ApplicantProfileRepository.save(profile) → one ExtractedFieldJpaEntity row per field
  │  5. ApplicantProfileResult.from(profile)
  ▼
ApplicantProfileController → ApplicantProfileResponse DTO → Client

Verification is the same shape, via ApplicantProfileVerificationService:
POST /api/v1/applicants/{applicantId}/profile/fields/verify
  { "fieldPath": "...", "action": "CONFIRM" | "CORRECT" | "REJECT", "correctedValue": ... }
  → ApplicantProfile.confirmField / correctField / rejectField
```

Every value set through `POST .../fields` is self-declared (typed directly by the applicant, no source document) and starts `USER_CONFIRMED`. `applicantId` is an opaque, unauthenticated `UUID` — neither this flow's application nor domain code inspects how it was obtained, so a future auth layer changes only the controller.

### 5.4 Document Intelligence Flow (Phase C)

The caller below is `ApplicantDocumentController` (`POST .../documents/passport` and `POST .../documents/{documentId}/confirm` — see `docs/api.md`), or a test constructing the commands directly.

```
Caller
  │  UploadApplicantDocumentCommand { applicantId, documentType=PASSPORT, imageBytes }
  ▼
DocumentIntelligenceService (application/document)
  │  1. DocumentStoragePort.store(...) → ApplicantDocument{status=UPLOADED} saved
  │  2. ImageQualityPort.assess(imageBytes)
  │       FAIL → ApplicantDocument.rejectForQuality() → DocumentExtractionResult.qualityRejected(...)  [STOP]
  │       PASS → continue
  │  3. OcrPort.extractText(imageBytes) → MrzParser.parse(...) → MrzData (or empty → treated as quality-reject too)
  │  4. buildMrzFieldCandidates(MrzData)
  │       passportNumber/dateOfBirth/expiryDate → own check digit → 0.95 / 0.40
  │       fullName/gender/issuingCountry/nationality → composite check digit → 0.95 / 0.40
  │  5. VisionExtractionPort.extract(imageBytes) → VisualPassportData (never throws; degrades to empty on failure)
  │  6. buildVisualFieldCandidates(VisualPassportData)
  │       PassportFieldValidator checks each field; not-found/unparseable → no candidate (never a guess)
  │       fails validation → 0.40 (LOW) | passes + not clearlyReadable → 0.70 (MEDIUM) | passes + clearlyReadable → 0.90 (HIGH)
  │  7. mergeWithCrossValidation(mrzCandidates, visualCandidates)
  │       path in one source only → passed through unchanged
  │       both sources agree → MRZ candidate kept (checksum-backed, stronger signal); visual reading discarded
  │       both sources disagree → NEITHER written under the plain path; both surface as
  │                                 path+".conflict.mrz" / path+".conflict.visual", still UNVERIFIED
  │  8. ApplicantDocument.markExtracted() → DocumentExtractionResult.extracted(candidates)
  ▼
Caller reviews candidates with the applicant, then:

ConfirmExtractedFieldsCommand { applicantId, documentId, fields: { path → finalValue, ... } }
  ▼
ExtractedFieldConfirmationService (application/document)
  │  for each accepted field: ApplicantProfile.setField(path, ExtractedField(value, value, confidence=1.0,
  │                                                        documentId, VerificationStatus.USER_CONFIRMED))
  │  ApplicantDocument.confirm() → ApplicantDocumentRepository.save(...)
  ▼
ApplicantProfileResult.from(profile) → Caller
```

**All confidence values above are heuristic/provisional extraction-signal strengths, not statistically calibrated probabilities** — `0.95` means "the MRZ check digit for this value matched," not "95% likely correct." No confidence value, however high, ever changes `VerificationStatus` on its own; every extracted candidate starts and stays `UNVERIFIED` until step 9 (`ExtractedFieldConfirmationService`) runs, which is the only place a document-derived field can reach `USER_CONFIRMED`.

**Vision-model requirement:** step 5 needs a genuinely multimodal chat model behind the app's single `ChatModel` bean. Under the default `openai` profile that's `gpt-4o-mini` (vision-capable). Under the `ollama` profile, this was initially configured as `gemma4:latest`, which does **not** work for this — verified live (not assumed): its own Ollama capability metadata claims `vision` and it carries real vision-tower GGUF tensors, but three independent live tests (through `VisionPassportExtractionAdapter`, and raw `curl` against both `/api/generate` and `/api/chat` with a byte-verified-correct image payload) all showed it failing to actually consume the image — it either reports no image was given, or hallucinates the raw bytes as garbled text. This was root-caused to Ollama's handling of this specific model tag, not to Spring AI (`OllamaChatModel` is verified correct at the bytecode level — it does convert `UserMessage.getMedia()` into Ollama's native `images` field) or to the adapter (unchanged by this finding; it's written purely against `ChatModel`/`UserMessage`/`Media`, so it already works correctly with any genuinely multimodal model behind either provider).

**First resolved (2026-09-02):** the `ollama` profile was set to `llava:7b`. Verified live end-to-end — through the real adapter against a real local Ollama instance, with a synthetic (not real) test passport image — `llava:7b` genuinely consumed the image and returned all 6 fields in the adapter's expected JSON shape, 5 of them matching the source image exactly (one digit dropped from the passport number, one date field misread — ordinary OCR-accuracy error, not the "never saw the image" failure mode `gemma4:latest` had). See `VisionPassportExtractionAdapterOllamaLiveIT` (opt-in, `-Dlive-ollama=true`) and `PROGRESS.md`'s 2026-09-02 entry for that result.

**Superseded (current default): `qwen2.5vl:7b`.** Further passport-extraction testing found `llava:7b` consumes the image but hallucinates confident-looking wrong values on fine-grained fields specifically — passport numbers and dates, exactly the fields that matter most for MRZ cross-validation — rather than the "digit dropped" ordinary OCR error the earlier note described. `qwen2.5vl:7b` is configured instead in `application-ollama.properties` for both RAG text queries and passport-image extraction (one model, one profile, no separate vision provider/config).

### 5.5 Visa Application Flow (Phase D)

The caller below is `ApplicationController` (`POST .../applications`, `POST .../applications/{applicationId}/withdraw`, `GET .../applications/{applicationId}/checklist`, `GET .../applications/{applicationId}/consistency-check` — see `docs/api.md`).

```
CreateApplicationCommand { applicantId, passportIso, destinationIso, visaTypeCode }
  ▼
ApplicationService.create(...)
  │  new Application(..., status=OPEN) → ApplicationRepository.save(...)
  ▼
ApplicationResult → Caller

GET .../checklist
  ▼
ChecklistService.getChecklist(applicantId, applicationId)
  │  1. ApplicationService.getOwned(applicantId, applicationId) — 404 (ApplicationNotFoundException) if not owned
  │  2. MetadataFilter(application.passportIso, destinationIso, visaTypeCode)
  │  3. VisaQueryCommand("What documents are required for a {visaTypeCode} visa application?", filter)
  │  4. VisaQueryService.answer(...) — the same RAG pipeline a normal visa query uses; no separate
  │       document-list data exists or is needed
  │       InsufficientContextException → caught here, converted to ChecklistResult with the fixed
  │       fallback text (NOT left to propagate — GlobalExceptionHandler's existing handler for that
  │       exception returns a VisaQueryResponse-shaped body, which doesn't match ChecklistResponse)
  ▼
ChecklistResult → Caller

GET .../consistency-check
  ▼
ConsistencyValidationService.checkConsistency(applicantId, applicationId)
  │  1. ApplicationRepository.findById(...) filtered by applicantId — 404 if not owned (ownership check
  │       only; the scan below only needs applicantId, but the URL is nested under the application)
  │  2. ApplicantProfileRepository.findByApplicantId(applicantId) — 404 (ApplicantProfileNotFoundException) if none
  │  3. scan profile.getFields() for key pairs ending ApplicantProfile.CONFLICT_SUFFIX_MRZ /
  │       CONFLICT_SUFFIX_VISUAL (left unresolved by DocumentIntelligenceService, §5.4 step 7)
  ▼
ConsistencyCheckResult{ consistent, conflicts: [FieldConflict{fieldPath, mrzReading, visualReading}, ...] } → Caller
```

**Scope deviation from `docs/VISA_APPLICATION_ASSISTANT_PROPOSAL.md`, confirmed via `AskUserQuestion`:** that proposal describes `ConsistencyValidationService` as a cross-*document*-type check (e.g. passport vs. NID). `DocumentType` only has `PASSPORT` today, so there is no second document type to compare against — the implementation above reuses the existing MRZ-vs-visual conflict posture from Phase C instead, which is the one genuinely implementable form of "surface conflicts, don't silently resolve them" available right now. A true cross-document check is deferred until a second `DocumentType` exists.

**`ApplicationStatus` scope, confirmed via `AskUserQuestion`:** the proposal names `Application`/`ApplicationStatus` without enumerating values, and Phase D has no status-transition endpoint beyond `withdraw`. Rather than define an enum value with no caller, `withdraw()` and the `WITHDRAWN` state were added together so the enum has real behavior from the start.

---

## 6. RAG Pipeline

### 6.1 Chunking Strategy

- **Algorithm:** Sliding window with sentence-boundary awareness
- **Chunk size:** 512 tokens (configurable via `rag.chunking.size`)
- **Overlap:** 64 tokens (configurable via `rag.chunking.overlap`)
- **Rationale:** Overlap preserves cross-boundary context; sentence-boundary awareness avoids mid-sentence splits

### 6.2 Embedding

- **Model:** `text-embedding-3-small` (OpenAI) or `nomic-embed-text` (Ollama)
- **Dimension:** 1536 / 768 respectively
- **Storage:** Vector stored in Qdrant; chunk ID and metadata stored in PostgreSQL

### 6.3 Hybrid Retrieval

Metadata filtering is applied **before** retrieval on both paths. This is a hard correctness requirement: an unfiltered retrieval call is a defect.

```
VisaQueryService builds MetadataFilter { passport_iso, destination_iso, visa_type_code }
    │
    ├──► EmbeddingPort.embed(queryText)
    │        │
    │        ▼
    │    QdrantVectorStoreAdapter.search(vector, metadataFilter, topK)
    │        │  payload filter applied at Qdrant query time — before cosine ranking
    │        ▼
    │    top-K semantic results (ScoredChunk list)
    │
    └──► DocumentRepository.fullTextSearch(queryText, metadataFilter, topK)
             │  WHERE passport_iso='BD' AND destination_iso='IN' AND visa_type_code='TOURIST'
             │  applied before ts_rank scoring
             ▼
         top-K keyword results (ScoredChunk list)
                         │
              Reciprocal Rank Fusion (RRF)
                         │
                 Merged ScoredChunk list
                         │
              ReRanker.rerank(mergedList)
                         │
                  Final RetrievedContext
```

### 6.4 Prompt Template

```
You are a visa information assistant. Answer ONLY based on the official documents provided below.
If the context does not contain enough information, respond exactly with:
"I couldn't find sufficient official information."
Never fabricate visa rules, fees, or processing times.

--- OFFICIAL CONTEXT ---
{context_chunks}

--- CITATIONS ---
{citations}

--- USER QUESTION ---
{user_query}

Respond in clear, structured English. Include the citation reference for every fact you state.
```

### 6.5 Confidence Scoring

The `ConfidenceScorer` domain service computes a score in `[0.0, 1.0]` from `RetrievedContext`:

| Factor | Weight |
|---|---|
| Mean fusion score of top-K chunks | 0.50 |
| Number of chunks retrieved (normalized) | 0.20 |
| Keyword match ratio (BM25 overlap) | 0.20 |
| Document recency (last updated) | 0.10 |

Responses with a score below `0.60` (configurable via `rag.confidence.threshold`) return the insufficient-context fallback. All inputs to `ConfidenceScorer` are plain Java types — no infrastructure objects enter the domain.

### 6.6 Citation Format

```json
{
  "citations": [
    {
      "ref": "[1]",
      "source": "Indian Visa Application Centre – Tourist Visa Guidelines 2024",
      "url": "https://indianvisaonline.gov.in/...",
      "lastUpdated": "2024-11-01",
      "chunkId": "uuid"
    }
  ]
}
```

---

## 7. Database Schema

### 7.1 Migration Strategy

All schema changes are managed by **Flyway**. SQL files live in `src/main/resources/db/migration/` and follow the naming convention:

```
V{version}__{description}.sql

V1__create_countries.sql
V2__create_visa_types.sql
V3__create_documents.sql
V4__create_document_chunks.sql
V5__create_query_audit.sql
V6__seed_reference_data.sql
V7__add_additional_india_visa_types.sql
V8__add_general_visa_type.sql
V9__create_source_check_log.sql
V10__create_applicant_profiles.sql
V11__create_extracted_fields.sql
V12__create_applicant_documents.sql
V14__create_applications.sql
```

**V13 (FK: `extracted_fields.source_document_id` → `applicant_documents.id`) is planned but not yet created** — deliberately gated until the Phase C implementation above and its tests were passing, per the plan that produced it. See `PROGRESS.md`'s Phase C section. **V14 (Phase D, `applications` table) was created before V13** since V13 is a separate, unrelated FK-hardening task — left as the next gap to close, not expanded into this session's Phase D scope.

`spring.jpa.hibernate.ddl-auto` is set to `validate` in production — Hibernate validates the schema against JPA entities but never modifies it. Flyway is the sole schema authority.

### 7.2 PostgreSQL Tables

```sql
-- V1__create_countries.sql
CREATE TABLE countries (
    id          BIGSERIAL    PRIMARY KEY,
    iso_code    CHAR(2)      NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- V2__create_visa_types.sql
CREATE TABLE visa_types (
    id          BIGSERIAL    PRIMARY KEY,
    country_id  BIGINT       NOT NULL REFERENCES countries(id),
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    UNIQUE (country_id, code)
);

-- V3__create_documents.sql
CREATE TABLE documents (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    country_id      BIGINT       NOT NULL REFERENCES countries(id),
    visa_type_id    BIGINT       NOT NULL REFERENCES visa_types(id),
    passport_iso    CHAR(2)      NOT NULL,
    title           VARCHAR(500) NOT NULL,
    source_url      TEXT,
    file_hash       CHAR(64)     NOT NULL,
    last_updated    DATE         NOT NULL,
    version         VARCHAR(20),
    ingested_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    active          BOOLEAN      NOT NULL DEFAULT true
);

-- V15__add_document_key.sql
ALTER TABLE documents ADD COLUMN document_key VARCHAR(200) NOT NULL;  -- after backfilling existing rows
CREATE UNIQUE INDEX idx_documents_active_key ON documents(document_key) WHERE active;

-- V4__create_document_chunks.sql
CREATE TABLE document_chunks (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID         NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index     INT          NOT NULL,
    content         TEXT         NOT NULL,
    token_count     INT          NOT NULL,
    qdrant_point_id UUID         NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_chunks_document ON document_chunks(document_id);

ALTER TABLE document_chunks
    ADD COLUMN tsv TSVECTOR
        GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;

CREATE INDEX idx_chunks_fts ON document_chunks USING GIN(tsv);

-- V5__create_query_audit.sql
CREATE TABLE query_audit (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    query_text          TEXT         NOT NULL,
    passport_iso        CHAR(2)      NOT NULL,
    destination_iso     CHAR(2)      NOT NULL,
    visa_type_code      VARCHAR(50),
    retrieved_chunk_ids UUID[]       NOT NULL,
    confidence_score    NUMERIC(4,3) NOT NULL,
    answer_text         TEXT         NOT NULL,
    model_id            VARCHAR(100) NOT NULL,
    latency_ms          INT          NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_created ON query_audit(created_at DESC);

-- V10__create_applicant_profiles.sql
CREATE TABLE applicant_profiles (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_id     UUID         NOT NULL,
    purpose_of_visit TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_modified_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_applicant_profiles_applicant ON applicant_profiles(applicant_id);

-- V11__create_extracted_fields.sql
CREATE TABLE extracted_fields (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_profile_id UUID         NOT NULL REFERENCES applicant_profiles(id) ON DELETE CASCADE,
    source_document_id   UUID,
    field_path           VARCHAR(200) NOT NULL,
    extracted_value      TEXT         NOT NULL,
    normalized_value     TEXT         NOT NULL,
    confidence           NUMERIC(4,3) NOT NULL CHECK (confidence >= 0.0 AND confidence <= 1.0),
    verification_status  VARCHAR(20)  NOT NULL
        CHECK (verification_status IN ('UNVERIFIED', 'USER_CONFIRMED', 'USER_CORRECTED', 'REJECTED')),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_extracted_fields_profile ON extracted_fields(applicant_profile_id);
CREATE UNIQUE INDEX idx_extracted_fields_profile_path ON extracted_fields(applicant_profile_id, field_path);

-- V12__create_applicant_documents.sql
CREATE TABLE applicant_documents (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_id  UUID         NOT NULL,
    document_type VARCHAR(30)  NOT NULL
        CHECK (document_type IN ('PASSPORT')),
    storage_path  TEXT         NOT NULL,
    uploaded_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    status        VARCHAR(20)  NOT NULL
        CHECK (status IN ('UPLOADED', 'QUALITY_REJECTED', 'EXTRACTED', 'CONFIRMED'))
);

CREATE INDEX idx_applicant_documents_applicant ON applicant_documents(applicant_id);

-- V14__create_applications.sql
CREATE TABLE applications (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_id     UUID         NOT NULL,
    passport_iso     VARCHAR(2)   NOT NULL,
    destination_iso  VARCHAR(2)   NOT NULL,
    visa_type_code   VARCHAR(30)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    status           VARCHAR(20)  NOT NULL
        CHECK (status IN ('OPEN', 'WITHDRAWN'))
);

CREATE INDEX idx_applications_applicant ON applications(applicant_id);
```

`extracted_fields.source_document_id` has no foreign key yet — `applicant_documents` didn't exist when V11 was written; it does now, so the FK (V13) is planned but deliberately not yet created, see above. `ApplicantProfileRepositoryAdapter` replaces every `extracted_fields` row for a profile on each save rather than diffing changes, so `created_at` on that table reflects "last synced," not true original creation — an accepted limitation, since nothing in Phase B reads that column for anything load-bearing.

### 7.3 Qdrant Collection

**Collection name:** `visa_chunks`

```json
{
  "collection_name": "visa_chunks",
  "vectors": {
    "size": 1536,
    "distance": "Cosine"
  },
  "payload_schema": {
    "chunk_id":        "keyword",
    "document_id":     "keyword",
    "passport_iso":    "keyword",
    "destination_iso": "keyword",
    "visa_type_code":  "keyword",
    "last_updated":    "datetime",
    "chunk_index":     "integer"
  }
}
```

All payload fields are indexed. Filters on `passport_iso`, `destination_iso`, and `visa_type_code` are applied at query time on the Qdrant side before cosine similarity scoring — never post-retrieval.

---

## 8. Future Roadmap

> This section's Phase 1–5 numbering is the original pre-implementation roadmap and is unrelated to the Visa Application Assistant's own Phase A–G roadmap in `docs/VISA_APPLICATION_ASSISTANT_PROPOSAL.md` §9 — don't conflate the two.

### Phase 1 — Current (Bangladesh → India MVP)

- [x] Spring Boot 3.5.x project scaffold
- [x] Clean Architecture package structure
- [x] Flyway schema migrations
- [x] Docker Compose (app + PostgreSQL + Qdrant)
- [x] Document ingestion pipeline (PDF + Tika)
- [x] Qdrant integration via Spring AI
- [x] Hybrid retrieval with pre-filtered vector + full-text search
- [x] Visa query REST endpoint with citations
- [x] Confidence scoring and fallback response

Phase 1 (Sprints 1–7) is complete as of 2026-08-19 — see `PROGRESS.md` for the sprint-by-sprint history. The Visa Application Assistant's Phase B (Applicant Profile, 2026-08-31), Phase C (Passport Document Intelligence, 2026-09-01 — MRZ + vision extraction, cross-field validation, upload/confirm controller endpoints; `ollama` profile's vision model settled on `qwen2.5vl:7b` after `llava:7b` was found to hallucinate fine-grained fields, see §5.4), and Phase D (Visa Application model, 2026-09-07 — `Application`/`ApplicationStatus`, document checklist reusing the existing RAG pipeline, profile consistency check; see §5.5) are also now complete, ahead of this section's own Phase 2/3, since all three were scoped and approved separately — see `docs/VISA_APPLICATION_ASSISTANT_PROPOSAL.md`.

### Phase 2 — Multi-Country Support

- [ ] Country/passport configuration-driven (no code changes per new route)
- [ ] Bulk ingestion CLI tool
- [ ] Document versioning and re-ingestion detection (SHA-256 dedup)
- [ ] Redis cache for frequently asked queries
- [ ] Admin API for document management

### Phase 3 — Quality and Observability

- [x] Answer-quality evaluation harness — golden set + deterministic scoring (source hit rate, MRR, fact recall, forbidden-phrase hallucination check, refusal recall / false refusal rate), run against the live endpoint (see below)
- [ ] Answer faithfulness evaluation using an LLM judge

**Answer-quality evaluation (added 2026-10-29).** Lives entirely in the test source tree under `com.neaz.travel.visa.evaluation` — a development/CI quality tool, not production code, so it adds no production package and no Maven dependency. `GoldenSetLoader` reads `src/test/resources/evaluation/golden-set.json` into `GoldenCase` records (categories `ANSWERABLE`, `GENERAL_FALLBACK`, `REFUSAL`); `EvaluationApiClient` sends each question to `POST /api/v1/visa/query` on a running service (black-box, so controllers, mappers and the `GlobalExceptionHandler` low-confidence fallback are all exercised); `CaseScorer` produces a `CaseScore` per case using case-insensitive substring matching — an LLM judge was deliberately not used, keeping runs free and reproducible; `EvaluationReport` aggregates the scores and renders `target/evaluation/report.md`. `AnswerQualityEvaluationTest` (`@Tag("evaluation")`) is gated on the `EVAL_BASE_URL` environment variable; `EVAL_REQUEST_TIMEOUT_SECONDS` overrides the 120s per-request timeout, and a failed request is scored as an errored case (`CaseScorer.error`) instead of aborting the run. Retrieval is measured through `citations[]`, which are already in rerank order — pre-rerank retrieval quality (precision@K, NDCG over all fused chunks) is not measured, since the endpoint doesn't expose it.
- [ ] Prometheus metrics + Grafana dashboard
- [ ] Structured logging (JSON via Logstash encoder)
- [ ] OpenTelemetry tracing across retrieval and generation

### Phase 4 — Extended RAG Modules

| Module | Description |
|---|---|
| Destination RAG | General travel information (currency, safety, entry requirements) |
| Airline Policy RAG | Baggage rules, cancellation policies from airline documents |
| Hotel Policy RAG | Booking terms, cancellation windows |
| Travel Planner Agent | Multi-step agentic flow combining all modules |

All future modules reuse `EmbeddingPort`, `VectorSearchPort`, `ChatPort`, and the ingestion pipeline — only new domain models and prompt templates are required.

### Phase 5 — Production Hardening

- [ ] Rate limiting per client IP / API key
- [ ] API key authentication (Spring Security)
- [ ] Kubernetes Helm chart
- [ ] Horizontal pod autoscaling on query volume
- [ ] Document freshness alerting (notify when official sources are updated)