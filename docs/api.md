# API Reference — Visa RAG Service

Six endpoints exist today: document ingestion, source-change review (both curator-facing), visa query (client-facing), and the applicant-profile trio — set a field, read a profile, verify a field (client-facing, Phase B of the Visa Application Assistant — see [`docs/VISA_APPLICATION_ASSISTANT_PROPOSAL.md`](VISA_APPLICATION_ASSISTANT_PROPOSAL.md)). All live under `/api/v1` and share one response envelope.

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
    "documentKey": "in/bd/tourist/required-documents",
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
| `metadata.documentKey` | string | yes | Stable identity of the logical document across versions (e.g. `in/bd/tourist/required-documents`): lowercase segments of letters, digits and hyphens separated by `/`, max 200 characters. Ingesting new content under a key that already has an active document supersedes it — see [Source Change Monitoring](#source-change-monitoring). Deliberately independent of `sourceUrl`, since one official source can yield several documents (one HCI Dhaka PDF covers ten visa types). |
| `metadata.destinationIso` | string | yes | ISO country code of the visa-issuing destination (e.g. `IN`). Must have seeded reference data (`countries`/`visa_types` tables). |
| `metadata.passportIso` | string | yes | ISO country code of the applicant's passport (e.g. `BD`). |
| `metadata.visaTypeCode` | string | yes | Visa type code (e.g. `TOURIST`, `BUSINESS`, or `GENERAL` for cross-cutting content — see [GENERAL fallback](#general-visa-type-fallback) below). Must exist for `destinationIso` in the seeded `visa_types` table. |
| `metadata.sourceUrl` | string | no | Official source URL. Used for citations and source-change monitoring; not for supersession. |
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

## POST /api/v1/applicants/{applicantId}/profile/fields

Sets one field on an applicant's profile. `applicantId` is an opaque, caller-supplied UUID — there is no authentication yet (see [Applicant Identity](#applicant-identity) below). If the applicant has no profile yet, one is created implicitly on the first field set.

Every value set through this endpoint is self-declared (typed directly by the applicant, not extracted from an uploaded document) and is immediately `USER_CONFIRMED` — there is nothing to verify a person's own unmediated input against.

### Request Body

```json
{
  "fieldPath": "personalInfo.fullName",
  "value": "Jane Doe"
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `fieldPath` | string | yes | One of `ApplicantProfile`'s canonical dot-paths — see [Field Paths](#field-paths) below. |
| `value` | string \| number \| object | yes | A plain string/number for most fields; a nested object (`line1`/`line2`/`city`/`postalCode`/`countryIso`) for the two address fields. Date fields take an ISO-8601 (`yyyy-MM-dd`) string. |

Example — setting an address field:

```json
{
  "fieldPath": "addressInfo.permanentAddress",
  "value": { "line1": "House 1, Road 2", "city": "Dhaka", "postalCode": "1200", "countryIso": "BD" }
}
```

### Response — 200 OK

Returns the full profile after the update — see [Profile Response Shape](#profile-response-shape) below.

### Errors

An unknown `fieldPath` or a `value` that doesn't match the field's declared type (e.g. a non-date string for a date field) returns `400 Bad Request` — see [Error Responses](#error-responses).

---

## GET /api/v1/applicants/{applicantId}/profile

Reads an applicant's full profile.

### Response — 200 OK

```json
{
  "success": true,
  "data": {
    "id": "d4e1a2b3-...",
    "applicantId": "6f2c9e10-...",
    "personalInfo": {
      "fullName": {
        "extractedValue": "Jane Doe",
        "normalizedValue": "Jane Doe",
        "confidence": 1.0,
        "sourceDocumentId": null,
        "verificationStatus": "USER_CONFIRMED"
      },
      "dateOfBirth": null,
      "gender": null,
      "placeOfBirth": null
    },
    "passportInfo": { "passportNumber": null, "issuingCountry": null, "issueDate": null, "expiryDate": null, "passportType": null },
    "nationalityInfo": { "nationality": null, "previousNationality": null },
    "addressInfo": { "permanentAddress": null, "currentAddress": null },
    "familyInfo": { "fatherName": null, "motherName": null, "spouseName": null, "maritalStatus": null },
    "employmentInfo": { "occupation": null, "employerName": null, "employerAddress": null, "monthlyIncome": null },
    "travelInfo": { "purposeOfVisit": null, "intendedArrivalDate": null, "intendedDurationDays": null, "portOfEntry": null },
    "lastModifiedAt": "2026-08-31T12:00:00Z"
  },
  "message": null,
  "timestamp": "2026-08-31T12:00:00Z"
}
```

A field that hasn't been set yet is `null` in its group, not omitted — every group is always present with all its keys.

### Errors

No profile exists yet for `applicantId` → `404 Not Found` — see [Error Responses](#error-responses).

---

## POST /api/v1/applicants/{applicantId}/profile/fields/verify

Applies a verification decision to one already-set field: accept it as-is, replace it with a corrected value, or reject it outright. This is the only way a field's `verificationStatus` changes after it was first set — see [Verification Workflow](#verification-workflow) below.

### Request Body

```json
{
  "fieldPath": "personalInfo.fullName",
  "action": "CORRECT",
  "correctedValue": "Jane D. Doe"
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `fieldPath` | string | yes | The field being verified; must already have a value set. |
| `action` | string | yes | One of `"CONFIRM"`, `"CORRECT"`, `"REJECT"`. |
| `correctedValue` | string \| number \| object | only for `CORRECT` | Same shape rules as `value` on the set-field endpoint. Must be omitted for `CONFIRM`/`REJECT`. |

### Response — 200 OK

Returns the full profile after the update — see [Profile Response Shape](#profile-response-shape) above.

### Errors

- `applicantId` has no profile → `404 Not Found`.
- `fieldPath` was never set on the profile → `400 Bad Request` (`"No value set for field '...'; cannot verify a field that hasn't been set"`).
- `action`/`correctedValue` mismatch (e.g. `CORRECT` with no `correctedValue`), or `correctedValue` doesn't match the field's declared type → `400 Bad Request`.

See [Error Responses](#error-responses).

---

## Profile Response Shape

All three applicant-profile endpoints return the same shape: `id`, `applicantId`, the seven grouped sections (`personalInfo`, `passportInfo`, `nationalityInfo`, `addressInfo`, `familyInfo`, `employmentInfo`, `travelInfo`), and `lastModifiedAt`. Every field inside a group is either `null` (not set yet) or an object with:

| Field | Type | Notes |
|---|---|---|
| `extractedValue` | varies | The value as originally submitted/extracted, before normalization. Preserved even after a correction, for audit. |
| `normalizedValue` | varies | The canonical value — what a future consumer (checklist, form preparation) should read. |
| `confidence` | number, `[0.0, 1.0]` | `1.0` for every field set through Phase B (self-declared); will vary once Phase C (OCR extraction) exists. |
| `sourceDocumentId` | UUID \| null | `null` for every field set through Phase B — there is no document upload yet (Phase C). |
| `verificationStatus` | string | `"UNVERIFIED"`, `"USER_CONFIRMED"`, `"USER_CORRECTED"`, or `"REJECTED"`. |

`travelInfo.purposeOfVisit` is the one exception — a plain string (or `null`), not an object, since it's free text with no provenance to track.

### Field Paths

```
personalInfo.fullName          personalInfo.dateOfBirth       personalInfo.gender            personalInfo.placeOfBirth
passportInfo.passportNumber    passportInfo.issuingCountry    passportInfo.issueDate         passportInfo.expiryDate        passportInfo.passportType
nationalityInfo.nationality    nationalityInfo.previousNationality
addressInfo.permanentAddress   addressInfo.currentAddress
familyInfo.fatherName          familyInfo.motherName          familyInfo.spouseName           familyInfo.maritalStatus
employmentInfo.occupation      employmentInfo.employerName    employmentInfo.employerAddress  employmentInfo.monthlyIncome
travelInfo.intendedArrivalDate travelInfo.intendedDurationDays travelInfo.portOfEntry         travelInfo.purposeOfVisit
```

### Verification Workflow

A field set via `POST .../profile/fields` is self-declared and starts `USER_CONFIRMED` (see that endpoint above). Phase C (passport document intelligence) produces genuinely `UNVERIFIED` candidates from MRZ/vision extraction (see [Document Intelligence](#document-intelligence-phase-c) below): an extracted field starts `UNVERIFIED`, and nothing downstream treats it as trustworthy until the applicant reviews it via the document flow's own confirm endpoint, which writes `USER_CONFIRMED` directly (this verify endpoint operates on fields already set on the profile, not on document candidates).

### Applicant Identity

`applicantId` is an opaque UUID with **no authentication or authorization** in Phase B — any caller who knows or guesses an `applicantId` can read and modify that profile. This is a deliberate, scoped-down starting point (see `docs/VISA_APPLICATION_ASSISTANT_PROPOSAL.md` §1), not an oversight: `domain`/`application` code never inspects how `applicantId` was obtained, so a future auth layer only needs to change how the controller derives it, not the layers beneath it.

---

## Document Intelligence (Phase C)

The passport-upload-to-candidates pipeline (`DocumentIntelligenceService`, `ExtractedFieldConfirmationService` in `application/document`) has two production endpoints below, plus a diagnostic vision-only endpoint. Requires Tesseract OCR configured (`ocr.tesseract.data-path` / `TESSDATA_PATH`) — the upload endpoint runs MRZ extraction before vision and rejects the upload if that fails, so it can't be exercised until Tesseract is set up on the host running the app.

- Uploading a passport image runs it through an image-quality gate, then two independent extraction sources — MRZ OCR (checksum-validated) and vision-model reading of the printed page (validated, self-reported readability) — cross-checked against each other where both supply the same field (passport number, DOB, expiry).
- Every candidate starts `UNVERIFIED`, regardless of confidence — confidence values are documented as heuristic/provisional extraction-signal strength, never a calibrated probability, and never on their own promote a field to verified.
- When MRZ and vision disagree on a field, neither is written under that field's normal path — both are exposed under `path + ".conflict.mrz"` / `path + ".conflict.visual"` instead, so a human resolves it rather than one source silently winning.
- Confirmation behaves exactly like the existing verify endpoint above: `confidence` becomes `1.0` and `VerificationStatus` becomes `USER_CONFIRMED`.
- Under the `ollama` profile, the vision-extraction step runs against `qwen2.5vl:7b` (the profile's current default — `gemma4:latest` was verified not to consume the image at all, and `llava:7b` consumed it but hallucinated wrong passport numbers/dates) — see `PROGRESS.md`'s Phase C section and `docs/ARCHITECTURE.md` §5.4 for the live verification of all three findings.

### POST /api/v1/applicants/{applicantId}/documents/passport

Multipart upload (`file` field, a passport image). Runs the full pipeline described above and returns field candidates for the applicant to review — nothing is written to the applicant's profile yet.

```bash
curl -X POST http://localhost:8081/api/v1/applicants/$(uuidgen)/documents/passport \
  -F "file=@/path/to/passport.jpg"
```

Response — 200 OK (extracted):

```json
{
  "documentId": "b3f...",
  "status": "EXTRACTED",
  "message": null,
  "issues": [],
  "fieldCandidates": {
    "passportInfo.passportNumber": { "value": "A1234567", "confidence": 0.95, "verificationStatus": "UNVERIFIED" }
  }
}
```

Response — 200 OK (quality-rejected):

```json
{
  "documentId": "b3f...",
  "status": "QUALITY_REJECTED",
  "message": "Please upload a clear, flat, well-lit passport scan/photo.",
  "issues": ["BLURRY", "POOR_LIGHTING"],
  "fieldCandidates": {}
}
```

`status` is `"EXTRACTED"` (candidates present, `issues` empty) or `"QUALITY_REJECTED"` (image too degraded to OCR reliably, or no MRZ could be located — `fieldCandidates` is empty). `message` is a one-sentence summary; `issues` carries the specific `ImageQualityIssue` reason code(s) so a client can show the applicant an actionable checklist instead of just the generic sentence — `BLURRY`, `SKEWED`, `POOR_LIGHTING`, `GLARE_OR_SHADOW`, or `MRZ_NOT_READABLE` (the last also covers the case where the image passes the pixel-level quality gate but Tesseract still can't parse a valid MRZ from it). A field key ending `.conflict.mrz` / `.conflict.visual` means the two sources disagreed on that field; resolve it before confirming.

### POST /api/v1/applicants/{applicantId}/documents/{documentId}/confirm

Applies the applicant's review of a document's field candidates to their profile. `documentId` comes from the upload response above.

```json
{
  "fields": {
    "passportInfo.passportNumber": "A1234567"
  }
}
```

Only the candidates the applicant chose to keep go in `fields` — unchanged or edited; a discarded candidate is simply left out. Each included value is written to the profile as `USER_CONFIRMED` with `confidence` `1.0`, regardless of its original extraction confidence, and the source document is marked `CONFIRMED`.

Response — 200 OK: the full profile, same shape as [Profile Response Shape](#profile-response-shape) above.

### Errors

- `documentId` doesn't exist, or belongs to a different `applicantId` → `404 Not Found` (both cases return the same error, to avoid leaking whether a document ID exists for another applicant).
- `fields` is empty → `400 Bad Request`.

See [Error Responses](#error-responses).

### Vision Preview (diagnostic, Phase C)

`POST /api/v1/applicants/{applicantId}/documents/passport/vision-preview` — multipart upload (`file` field, an image). **Not the production upload flow above.** Calls `VisionExtractionPort` directly — bypasses the image-quality gate, OCR, MRZ parsing, and persistence entirely — so it can be used to check what the configured vision model (`qwen2.5vl:7b` under `ollama`, `gpt-4o-mini` under `openai`) reads off a real image before Tesseract is set up. `applicantId` only keeps the URL shape consistent with the rest of this API; nothing is stored or associated with that applicant.

```bash
curl -X POST http://localhost:8081/api/v1/applicants/$(uuidgen)/documents/passport/vision-preview \
  -F "file=@/path/to/passport.jpg"
```

Response shape mirrors `VisualPassportData` — six fields (`passportNumber`, `dateOfBirth`, `expiryDate`, `issueDate`, `placeOfBirth`, `passportType`), each `{ "value": string|null, "present": boolean, "clearlyReadable": boolean }`. Live-verified against a real local Ollama + `llava:7b` and a real HTTP multipart upload — see `PROGRESS.md`'s 2026-09-02 entry. Per-field accuracy varies between runs (observed: correct on one run, two fields shifted onto the wrong keys on another) — this is a diagnostic tool for inspecting raw model output, not a substitute for the confirmation step the real pipeline requires before any value is trusted.

Full design: [`docs/ARCHITECTURE.md`](ARCHITECTURE.md#54-document-intelligence-flow-phase-c), [`PROGRESS.md`](../PROGRESS.md#phase-c--passport-document-intelligence-core-done-2026-09-01).

---

## Visa Application (Phase D)

An `Application` is the passport/destination/visa-type scope an applicant works against — creating one, withdrawing it, getting a document checklist, and checking the applicant's profile for unresolved conflicts. `applicantId` is an opaque, unauthenticated identifier, same posture as the rest of this API.

### POST /api/v1/applicants/{applicantId}/applications

```bash
curl -X POST http://localhost:8081/api/v1/applicants/$(uuidgen)/applications \
  -H "Content-Type: application/json" \
  -d '{"passportIso": "BD", "destinationIso": "IN", "visaTypeCode": "TOURIST"}'
```

Response — 200 OK:

```json
{
  "success": true,
  "data": {
    "id": "c3f...",
    "applicantId": "a1b...",
    "passportIso": "BD",
    "destinationIso": "IN",
    "visaTypeCode": "TOURIST",
    "status": "OPEN",
    "createdAt": "2026-09-07T10:00:00Z"
  },
  "message": null,
  "timestamp": "2026-09-07T10:00:00Z"
}
```

### POST /api/v1/applicants/{applicantId}/applications/{applicationId}/withdraw

No body. Transitions `OPEN` → `WITHDRAWN`. Returns the same `ApplicationResponse` shape as create, with `status: "WITHDRAWN"`.

### GET /api/v1/applicants/{applicantId}/applications/{applicationId}/checklist

Answers "what documents does this application need" by asking that exact question of the same RAG pipeline `POST /api/v1/visa/query` uses, scoped to the application's own `passportIso`/`destinationIso`/`visaTypeCode` — there is no separately maintained document-list data.

Response — 200 OK:

```json
{
  "success": true,
  "data": {
    "applicationId": "c3f...",
    "checklistText": "You will need a valid passport, a passport-sized photograph, proof of accommodation...",
    "citations": [
      { "ref": "[1]", "source": "India Tourist Visa Checklist", "url": "https://...", "lastUpdated": "2024-11-01", "chunkId": "uuid" }
    ],
    "confidenceScore": 0.87
  },
  "message": null,
  "timestamp": "2026-09-07T10:00:00Z"
}
```

If the retrieved context's confidence falls below the configured threshold, `checklistText` is the fixed `"I couldn't find sufficient official information."` sentence, `citations` is empty, and `confidenceScore` reflects the real (low) score — `200`/`success: true`, same fallback posture as a normal low-confidence visa query, not an error.

### GET /api/v1/applicants/{applicantId}/applications/{applicationId}/consistency-check

Scans the applicant's profile for fields where `DocumentIntelligenceService`'s MRZ and vision extraction disagreed and the applicant never resolved the conflict (the `path + ".conflict.mrz"` / `".conflict.visual"` keys from the document-upload response, §"Document Intelligence" above). **This is not yet a true cross-document check** (e.g. passport vs. a future NID) — `DocumentType` only has `PASSPORT` today, so there is nothing else to cross-check against yet; see `docs/ARCHITECTURE.md` §5.5 for the full scope note.

Response — 200 OK:

```json
{
  "success": true,
  "data": {
    "applicantId": "a1b...",
    "consistent": false,
    "conflicts": [
      { "fieldPath": "personalInfo.fullName", "mrzValue": "JANE DOE", "visualValue": "JANE DOEE" }
    ]
  },
  "message": null,
  "timestamp": "2026-09-07T10:00:00Z"
}
```

`consistent` is `true` iff `conflicts` is empty.

### Errors

- `applicationId` doesn't exist, or belongs to a different `applicantId` → `404 Not Found` (both endpoints above that take an `applicationId`, plus withdraw).
- Withdrawing an already-`WITHDRAWN` application → `400 Bad Request`.
- No `ApplicantProfile` exists yet for the consistency-check endpoint → `404 Not Found`.

See [Error Responses](#error-responses).

---

## Source Change Monitoring

A scheduled job (`SourceChangeMonitor`, interval configurable via `source-monitoring.check-interval-ms`, default 24h) checks every active document that has a `sourceUrl`: it fetches the live content, hashes it the same way ingestion hashes stored content, and compares. Results are logged to `source_check_log` as `UNCHANGED`, `CHANGED`, or `FETCH_FAILED` — nothing is written to the knowledge base automatically.

The full workflow is human-in-the-loop by design (no auto re-ingestion, so nothing unreviewed ever becomes retrievable content):

1. The monitor runs on schedule and flags changed sources.
2. A curator checks `GET /api/v1/ingestion/pending-changes` for anything flagged `CHANGED`.
3. The curator confirms the change is legitimate (not a scrape glitch or a session-driven portal artifact — see the caveat below) and re-ingests via `POST /api/v1/ingestion/documents`.
4. `DocumentIngestionService` detects there is already an active document with the same `documentKey` and different content, and deactivates the prior version in **both** PostgreSQL (`documents.active = false`, swapped atomically with the new version's insert) and Qdrant (payload-only update, `active = false` on every chunk of the old document, applied after the new chunks are upserted) — so the old version stops being retrievable in either store, without needing to re-embed or delete anything. A partial unique index (`idx_documents_active_key`) guarantees at most one active document per key at the database level.

Supersession is keyed by `documentKey`, not `sourceUrl`: several documents can share one source URL (e.g. the ten per-visa-type checklists cut from one HCI Dhaka PDF) and stay active side by side.

**Caveat:** `indianvisaonline.gov.in` and `indianvisa-bangladesh.nic.in` are session/form-driven portals that a plain HTTP fetch cannot meaningfully check — expect these to mostly report `FETCH_FAILED` ("not automatically checkable") rather than a reliable `CHANGED`/`UNCHANGED` signal. Monitoring is reliable for static sources such as PDFs.

---

## Error Responses

All exceptions are translated by a single `@RestControllerAdvice` (`GlobalExceptionHandler`) into the same `ApiResponse` envelope — no endpoint ever returns a bare stack trace or a framework-default error body.

| Condition | HTTP Status | `data` | `message` |
|---|---|---|---|
| Request body fails `@Valid` validation (e.g. blank `query`, missing `metadata`) | `400 Bad Request` | `null` | Joined field errors, e.g. `"query: must not be blank; passport: must not be blank"` |
| A referenced `Document` id doesn't exist | `404 Not Found` | `null` | `"Document not found: <uuid>"` |
| No `ApplicantProfile` exists for the requested `applicantId` | `404 Not Found` | `null` | `"Applicant profile not found for applicantId: <uuid>"` |
| `applicationId` doesn't exist, or belongs to a different `applicantId` | `404 Not Found` | `null` | `"Application not found: <uuid>"` |
| Malformed applicant-profile input — unknown `fieldPath`, a `value`/`correctedValue` that doesn't match the field's declared type, or verifying a field that was never set | `400 Bad Request` | `null` | The exception's own message |
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
