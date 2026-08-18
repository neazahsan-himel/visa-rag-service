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
│         VisaQueryController    IngestionController           │
└──────────┬───────────────────────────────┬───────────────────┘
           │                               │
┌──────────▼───────────────────────────────▼───────────────────┐
│                    Application Layer                         │
│                                                              │
│  query/                       ingestion/                     │
│    VisaQueryService              DocumentIngestionService    │
│    retrieval/                                                │
│      HybridRetriever          port/                          │
│      ReRanker                   EmbeddingPort               │
│    prompt/                      VectorSearchPort            │
│      VisaPromptTemplate          ChatPort                   │
│    citation/                                                 │
│      CitationBuilder                                         │
└──────────┬───────────────────────────────┬───────────────────┘
           │                               │
┌──────────▼───────────────────────────────▼───────────────────┐
│                       Domain Layer                           │
│   model/  VisaQuery  VisaAnswer  Document  Chunk             │
│           RetrievedContext  DocumentMetadata                 │
│   repository/  DocumentRepository  VisaQueryLogRepository   │
│   service/     ConfidenceScorer                             │
└──────────────────────────────────────────────────────────────┘
                           ▲
                           │ implements
┌──────────────────────────┴───────────────────────────────────┐
│                   Infrastructure Layer                       │
│                                                              │
│  persistence/   JPA entities, Spring Data repos, adapters   │
│  vectorstore/   QdrantVectorStoreAdapter                    │
│  embedding/     EmbeddingAdapter                            │
│  llm/           ChatAdapter                                 │
│  document/      TikaDocumentParser, ChunkingStrategy,       │
│                 FileSystemDocumentLoader, UrlDocumentLoader, │
│                 MetadataExtractor                            │
└──────────────────────────────────────────────────────────────┘
```

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
```

No package may be created outside this structure without an explicit architectural decision.

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

- `exception/` — `VisaRagException`, `DocumentNotFoundException`, `InsufficientContextException`
- `model/` — `PagedResult<T>`, `ApiResponse<T>`
- `util/` — `TextSanitizer`, `MetadataValidator`

### `presentation`

Spring MVC controllers, DTOs, and request/response mappers. Depends only on `application` commands and results.

- `controller/VisaQueryController` — `POST /api/v1/visa/query`
- `controller/IngestionController` — `POST /api/v1/ingestion/documents`
- `dto/` — `VisaQueryRequest`, `VisaQueryResponse`, `IngestionRequest`, `IngestionResponse`
- `mapper/` — maps between DTOs and application-layer commands/results

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

- `DocumentIngestionService` — receives an `IngestDocumentCommand` and delegates to infrastructure for parsing, chunking, embedding, and persistence; the service itself contains no parsing or I/O logic

#### `application/command`

Immutable input objects passed from `presentation` to `application`.

- `VisaQueryCommand` — query text, passport ISO, destination ISO, optional visa type code
- `IngestDocumentCommand` — document path or URL, metadata fields

#### `application/result`

Immutable output objects returned from `application` to `presentation`.

- `VisaQueryResult` — answer text, citations, confidence score, model ID, latency
- `IngestionResult` — document ID, chunk count, status

#### `application/port`

Outbound port interfaces defined by the application layer and implemented by infrastructure.

- `EmbeddingPort` — `embed(text: String): float[]`
- `VectorSearchPort` — `search(query: float[], filter: MetadataFilter, topK: int): List<ScoredChunk>`
- `ChatPort` — `generate(prompt: String, modelId: String): String`
- `DocumentParsingPort` — `parse(source: DocumentSource): String`
- `DocumentChunkingPort` — `chunk(text: String, metadata: DocumentMetadata): List<Chunk>`

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

#### `domain/repository`

Interface contracts implemented in `infrastructure/persistence`.

- `DocumentRepository` — CRUD for `Document` aggregates and `Chunk` entities
- `VisaQueryLogRepository` — append-only audit log writes

#### `domain/service`

- `ConfidenceScorer` — domain service; computes a `[0.0, 1.0]` score from a `RetrievedContext` using mean fusion score, chunk count, keyword match ratio, and document recency; operates only on plain Java types

### `infrastructure`

Implements domain repository interfaces and application port interfaces. May import Spring, JPA, Qdrant client, Spring AI, and all third-party libraries. Must never be imported by `domain` or `application`.

#### `infrastructure/persistence`

- JPA `@Entity` classes (`DocumentJpaEntity`, `ChunkJpaEntity`, `QueryAuditJpaEntity`, `CountryJpaEntity`, `VisaTypeJpaEntity`)
- Spring Data `JpaRepository` interfaces
- Adapter classes that implement `DocumentRepository` and `VisaQueryLogRepository` by delegating to the Spring Data repositories
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
```

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
```

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

### Phase 1 — Current (Bangladesh → India MVP)

- [x] Spring Boot 3.5.x project scaffold
- [ ] Clean Architecture package structure
- [ ] Flyway schema migrations
- [ ] Docker Compose (app + PostgreSQL + Qdrant)
- [ ] Document ingestion pipeline (PDF + Tika)
- [ ] Qdrant integration via Spring AI
- [ ] Hybrid retrieval with pre-filtered vector + full-text search
- [ ] Visa query REST endpoint with citations
- [ ] Confidence scoring and fallback response

### Phase 2 — Multi-Country Support

- [ ] Country/passport configuration-driven (no code changes per new route)
- [ ] Bulk ingestion CLI tool
- [ ] Document versioning and re-ingestion detection (SHA-256 dedup)
- [ ] Redis cache for frequently asked queries
- [ ] Admin API for document management

### Phase 3 — Quality and Observability

- [ ] Retrieval evaluation harness (precision@K, NDCG)
- [ ] Answer faithfulness evaluation using an LLM judge
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