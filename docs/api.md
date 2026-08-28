# API Reference — Visa RAG Service

Three endpoints exist today: document ingestion, source-change review (both curator-facing), and visa query (client-facing). All live under `/api/v1` and share one response envelope.

---

## Base URL

```
http://localhost:8081
```

(`server.port=8081` — see `application.properties`; 8080 is claimed by a local Docker Desktop Kubernetes cluster on the reference dev machine.)

---

## Response Envelope

Every endpoint — success or error — returns `ApiResponse<T>`:

```json
{
  "success": true,
  "data": { },
  "message": null,
  "timestamp": "2026-08-19T16:30:00Z"
}
```

| Field | Type | Description |
|---|---|---|
| `success` | boolean | `true` on success, `false` on error |
| `data` | object \| null | the endpoint's payload; `null` on error |
| `message` | string \| null | human-readable message; `null` on success unless explicitly set |
| `timestamp` | ISO-8601 instant | when the response was constructed |

---

## POST /api/v1/ingestion/documents

Ingests one document: parses it, splits it into chunks, embeds each chunk, persists the document and chunks to PostgreSQL, and upserts the chunk vectors to Qdrant. Re-ingesting a document whose extracted text hashes identically to an already-stored one is detected as a duplicate and not re-processed.

### Request Body

```json
{
  "path": "India/Tourist/hci-dhaka-visa-documents.md",
  "title": "India Tourist Visa – Required Documents (HCI Dhaka)",
  "metadata": {
    "destinationIso": "IN",
    "passportIso": "BD",
    "visaTypeCode": "TOURIST",
    "sourceUrl": "https://hcidhaka.gov.in/...",
    "lastUpdated": "2026-01-01",
    "version": "v1"
  }
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `path` | string | yes | Resolved against the `knowledge-base.root` config (default `knowledge-base/`). A path that would escape that root is rejected. |
| `title` | string | yes | Human-readable document title; also `Citation.source` in query responses. |
| `metadata.destinationIso` | string | yes | ISO country code of the visa-issuing destination (e.g. `IN`). Must have seeded reference data (`countries`/`visa_types` tables). |
| `metadata.passportIso` | string | yes | ISO country code of the applicant's passport (e.g. `BD`). |
| `metadata.visaTypeCode` | string | yes | Visa type code (e.g. `TOURIST`, `BUSINESS`, or `GENERAL` for cross-cutting content — see [GENERAL fallback](#general-visa-type-fallback) below). Must exist for `destinationIso` in the seeded `visa_types` table. |
| `metadata.sourceUrl` | string | no | Official source URL. |
| `metadata.lastUpdated` | date (`yyyy-MM-dd`) | yes | Last-updated date of the source document; feeds `ConfidenceScorer`'s recency factor. |
| `metadata.version` | string | no | Free-form version label. |

### Response — 200 OK

```json
{
  "success": true,
  "data": {
    "documentId": "b3f1c8a2-...",
    "chunkCount": 4,
    "status": "CREATED"
  },
  "message": null,
  "timestamp": "2026-08-19T16:30:00Z"
}
```

`status` is `"CREATED"` for a new document, or `"DUPLICATE"` if a document with identical extracted text already exists — in which case `documentId`/`chunkCount` refer to the **existing** document, and nothing is re-ingested.

### Errors

See [Error Responses](#error-responses).

---

## GET /api/v1/ingestion/pending-changes

Lists every unreviewed source-change result flagged `CHANGED` by the scheduled `SourceChangeMonitor` (see [Source Change Monitoring](#source-change-monitoring) below). Returns an empty array when nothing is pending — that is not an error.

### Response — 200 OK

```json
{
  "success": true,
  "data": [
    {
      "checkId": "6a1e2c3d-...",
      "documentId": "b3f1c8a2-...",
      "documentTitle": "India Tourist Visa – Required Documents (HCI Dhaka)",
      "sourceUrl": "https://hcidhaka.gov.in/...",
      "checkedAt": "2026-08-19T02:00:00Z",
      "detectedHash": "3a7bd3e2360a3d..."
    }
  ],
  "message": null,
  "timestamp": "2026-08-19T16:30:00Z"
}
```

| Field | Type | Notes |
|---|---|---|
| `checkId` | UUID | Identifier of the underlying `source_check_log` row. |
| `documentId` | UUID | The active document whose source appears to have changed. |
| `documentTitle` | string | The document's title, so a curator can recognize it without a lookup. |
| `sourceUrl` | string | The official source URL that was checked. |
| `checkedAt` | ISO-8601 instant | When the change was detected. |
| `detectedHash` | string | SHA-256 hex of the live source's extracted text, for comparison against the stored version. |

A curator acts on a flagged entry by re-ingesting the source through `POST /api/v1/ingestion/documents` (see [Source Change Monitoring](#source-change-monitoring)) — there is no endpoint to mark a row reviewed without re-ingesting; `SourceCheckLogRepository.markReviewed` exists at the domain layer but has no HTTP surface yet.

### Errors

See [Error Responses](#error-responses).

---

## POST /api/v1/visa/query

Answers a visa question, grounded only in retrieved official-document chunks scoped to the given passport/destination/visa-type. Runs hybrid retrieval (vector + full-text search, RRF-merged), reranks, scores confidence, and either generates a cited answer or returns the fixed no-hallucination fallback.

### Request Body

```json
{
  "query": "What documents do I need for an Indian tourist visa as a Bangladeshi passport holder?",
  "passport": "BD",
  "destination": "IN",
  "visaType": "TOURIST"
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `query` | string | yes | Natural-language question. |
| `passport` | string | yes | ISO code of the applicant's passport. |
| `destination` | string | yes | ISO code of the visa-issuing destination. |
| `visaType` | string | no | Visa type code to scope retrieval. `null`/omitted searches all visa types for the destination. |

### Response — 200 OK (answer found)

```json
{
  "success": true,
  "data": {
    "answer": "You will need: a passport valid for at least 6 months [1], one recent passport-size photograph [1][2], proof of residence [1], proof of financial soundness [1], and proof of profession [1].",
    "citations": [
      {
        "ref": "[1]",
        "source": "India Tourist Visa – Required Documents (HCI Dhaka)",
        "url": "https://hcidhaka.gov.in/...",
        "lastUpdated": "2026-01-01",
        "chunkId": "9e2a7f10-..."
      },
      {
        "ref": "[2]",
        "source": "India Visa – Photo Requirements",
        "url": "https://hcidhaka.gov.in/...",
        "lastUpdated": "2026-01-01",
        "chunkId": "1c44db55-..."
      }
    ],
    "confidenceScore": 0.6263
  },
  "message": null,
  "timestamp": "2026-08-19T16:30:00Z"
}
```

| Field | Type | Notes |
|---|---|---|
| `answer` | string | Every factual claim is followed by the bracket reference(s) of the excerpt(s) it came from. |
| `citations` | array | One entry per numbered excerpt actually cited; `ref` matches the bracket numbers in `answer`. |
| `confidenceScore` | number, `[0.0, 1.0]` | Weighted score from `ConfidenceScorer` (mean fusion score 0.50, chunk count 0.20, keyword match 0.20, recency 0.10). |

### Response — 200 OK (insufficient context)

When `confidenceScore` would fall below `rag.confidence.threshold` (default `0.60`), the service does **not** call the LLM — it returns the fixed no-hallucination fallback instead, still as a normal (`success: true`) response:

```json
{
  "success": true,
  "data": {
    "answer": "I couldn't find sufficient official information.",
    "citations": [],
    "confidenceScore": 0.42
  },
  "message": null,
  "timestamp": "2026-08-19T16:30:00Z"
}
```

This is treated as a valid business outcome, not an API error — a RAG system saying "I don't know" grounded in an actual confidence score is the correct behavior per this project's no-hallucination principle, so the HTTP status stays `200` and `success` stays `true`.

### GENERAL visa-type fallback

A document ingested with `visaTypeCode: "GENERAL"` is cross-cutting content (fee schedules, application instructions, FAQs) that applies to every visa type for its destination. When a query specifies a `visaType`, retrieval matches that type **or** `GENERAL` on both the vector-search and full-text-search paths — so `GENERAL` documents can always surface alongside type-specific ones, and their citations appear in the same `citations` array.

### Errors

See [Error Responses](#error-responses).

---

## Source Change Monitoring

A scheduled job (`SourceChangeMonitor`, interval configurable via `source-monitoring.check-interval-ms`, default 24h) checks every active document that has a `sourceUrl`: it fetches the live content, hashes it the same way ingestion hashes stored content, and compares. Results are logged to `source_check_log` as `UNCHANGED`, `CHANGED`, or `FETCH_FAILED` — nothing is written to the knowledge base automatically.

The full workflow is human-in-the-loop by design (no auto re-ingestion, so nothing unreviewed ever becomes retrievable content):

1. The monitor runs on schedule and flags changed sources.
2. A curator checks `GET /api/v1/ingestion/pending-changes` for anything flagged `CHANGED`.
3. The curator confirms the change is legitimate (not a scrape glitch or a session-driven portal artifact — see the caveat below) and re-ingests via `POST /api/v1/ingestion/documents`.
4. `DocumentIngestionService` detects there is already an active document for that `sourceUrl` with different content, and deactivates the prior version in **both** PostgreSQL (`documents.active = false`) and Qdrant (payload-only update, `active = false` on every chunk of the old document) — so the old version stops being retrievable in either store, without needing to re-embed or delete anything.

**Caveat:** `indianvisaonline.gov.in` and `indianvisa-bangladesh.nic.in` are session/form-driven portals that a plain HTTP fetch cannot meaningfully check — expect these to mostly report `FETCH_FAILED` ("not automatically checkable") rather than a reliable `CHANGED`/`UNCHANGED` signal. Monitoring is reliable for static sources such as PDFs.

---

## Error Responses

All exceptions are translated by a single `@RestControllerAdvice` (`GlobalExceptionHandler`) into the same `ApiResponse` envelope — no endpoint ever returns a bare stack trace or a framework-default error body.

| Condition | HTTP Status | `data` | `message` |
|---|---|---|---|
| Request body fails `@Valid` validation (e.g. blank `query`, missing `metadata`) | `400 Bad Request` | `null` | Joined field errors, e.g. `"query: must not be blank; passport: must not be blank"` |
| A referenced `Document` id doesn't exist | `404 Not Found` | `null` | `"Document not found: <uuid>"` |
| Any other deliberate domain/application error (e.g. ingestion path escapes the knowledge-base root) | `400 Bad Request` | `null` | The exception's own message — safe to expose, since these are raised deliberately by application code, not leaked internals |
| Anything unexpected — including unseeded country/visa-type reference data for the requested `destinationIso`/`visaTypeCode` | `500 Internal Server Error` | `null` | `"An unexpected error occurred. Please try again later."` — full details are logged server-side only, never returned to the client |

Example — validation failure:

```json
{
  "success": false,
  "data": null,
  "message": "query: must not be blank; passport: must not be blank",
  "timestamp": "2026-08-19T16:30:00Z"
}
```

Note that **low confidence is not an error** — it is documented separately above under `POST /api/v1/visa/query`, since it returns `200`/`success: true` with a valid `VisaQueryResponse`, not an entry in this table.
