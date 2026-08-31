# Visa Application Assistant — Architecture Proposal

**Status:** Proposal only. Nothing in this document is implemented. No Flyway migrations, no new Maven
dependencies, and no code have been created from this proposal — it is written for review and approval
before Phase B begins.

**Context:** Phase 1 MVP (Sprints 1–7: ingestion, hybrid RAG query pipeline, error handling/tests/docs,
source-change monitoring & version supersession) is complete. This document proposes the next product
foundation on top of it:

```
upload passport/NID/documents → OCR/extraction → normalized Applicant Profile
  → visa requirement RAG (existing pipeline) → document checklist → missing-document detection
  → cross-document consistency validation → application form preparation
```

---

## 0. Design constraints carried over from Phase 1

These are non-negotiable per `CLAUDE.md` / `DEVELOPMENT_RULES.md` and shape every section below:

- Clean Architecture, strict dependency rule (`presentation → application → domain ← infrastructure`).
- `domain` stays pure Java — no Spring/JPA/Tika/PDFBox types leak in.
- Constructor injection only; no field injection.
- No new package outside the approved structure without raising it as a question first (Sprint 7 set the
  precedent: `application/monitoring/` was proposed via `AskUserQuestion` before being created).
- No fabrication: AI-extracted values are never presented as ground truth without a visible confidence
  and verification state.
- Multi-country/multi-visa-type design — nothing India-specific baked into domain or application code.

---

## 1. Applicant Profile

### The core problem
OCR/extraction output is untrusted until a human confirms it. The domain model must never collapse
"what the model read" and "what is true" into one field. Every profile field is a **provenance-carrying
value**, not a raw string.

### `domain/model/ExtractedField<T>`

```java
public record ExtractedField<T>(
        T extractedValue,       // raw OCR/extraction output, as read
        T normalizedValue,      // canonicalized (e.g. date parsed to ISO, name case-folded)
        double confidence,      // [0.0, 1.0] from the extraction port
        UUID sourceDocumentId,  // which ApplicantDocument this came from
        VerificationStatus verificationStatus) {
}
```

```java
public enum VerificationStatus { UNVERIFIED, USER_CONFIRMED, USER_CORRECTED, REJECTED }
```

`normalizedValue` and `extractedValue` are intentionally both `T`, not `String` — a date field carries
`LocalDate`, a passport number carries a validated `String` wrapper, etc. `USER_CORRECTED` means the user
overwrote `normalizedValue` directly; the original `extractedValue` is retained for audit even after
correction.

### `domain/model/ApplicantProfile` (aggregate root)

Composed of value objects, each field individually wrapped in `ExtractedField<T>` where it originates
from a document, or a plain value where the user types it directly (no extraction involved — e.g. a
travel purpose free-text field has no provenance to track):

| Sub-model | Fields (each `ExtractedField<T>` unless noted) |
|---|---|
| `PersonalInfo` | fullName, dateOfBirth, gender, placeOfBirth |
| `PassportInfo` | passportNumber, issuingCountry, issueDate, expiryDate, passportType |
| `NationalityInfo` | nationality, previousNationality (nullable) |
| `AddressInfo` | permanentAddress, currentAddress (both structured: line1/line2/city/postalCode/countryIso) |
| `FamilyInfo` | fatherName, motherName, spouseName (nullable), maritalStatus |
| `EmploymentInfo` | occupation, employerName, employerAddress, monthlyIncome (nullable — not every visa type needs it) |
| `TravelInfo` | purposeOfVisit (plain, user-entered), intendedArrivalDate, intendedDurationDays, portOfEntry (nullable) |

`ApplicantProfile` itself is a mutable entity (like `Document`) — fields get corrected over time as new
documents are added or the user edits — with an `id: UUID`, `ownerId` (whatever identifies "whose
profile this is" — see open question below), and `lastModifiedAt`.

**Open question to resolve before Phase B, not now:** this system currently has no user/account model at
all — `query_audit` logs are anonymous. An `ApplicantProfile` needs an owner. Do we introduce a minimal
`applicant_id` concept (opaque UUID, no auth) for Phase B, or does an auth system need to land first? I'd
default to an opaque `applicant_id` (no login) for Phase B, deferring real auth to a later phase, but this
is a real product decision, not an implementation detail — flagging it rather than deciding it here.

### Why not just copy every OCR field 1:1
Two documents can disagree (NID spells a name differently than the passport). Collapsing to one string
per field would silently pick a winner, which is exactly the kind of silent conflict resolution the
existing RAG pipeline already refuses to do for source documents (Sprint 4's processing-time conflict was
left flagged, not resolved). `ExtractedField` gives cross-document consistency validation (§ below)
something to actually compare.

---

## 2. Document Intelligence — Ports

New `application/port` interfaces, following the exact shape of the existing `EmbeddingPort` /
`DocumentParsingPort` precedent (application defines the interface, infrastructure implements it):

```java
public interface OcrPort {
    OcrResult extractText(DocumentSource source); // reuses existing DocumentSource value type
}

public interface DocumentClassificationPort {
    DocumentType classify(OcrResult ocrResult); // PASSPORT, NID, BANK_STATEMENT, PHOTO, ...
}

public interface FieldExtractionPort {
    Map<String, ExtractedFieldCandidate> extractFields(OcrResult ocrResult, DocumentType type);
}

public interface DocumentValidationPort {
    DocumentValidationResult validate(ApplicantDocument document); // expiry check, tamper heuristics, legibility
}
```

`ExtractedFieldCandidate` is an application-layer DTO (raw value + confidence, pre-normalization) that an
application-layer service turns into domain `ExtractedField<T>` instances — normalization logic (parsing
a date string, canonicalizing a name) is a domain or application concern, not infrastructure's, matching
how `ConfidenceScorer` stays pure Java today.

### Infrastructure split
- `infrastructure/document/ocr/` — e.g. a Tesseract or cloud-OCR adapter implementing `OcrPort`. **Not
  decided yet** — this needs its own approval pass (self-hosted Tesseract vs. a cloud OCR API is a real
  cost/accuracy/privacy tradeoff, especially given § 7 below). Flagging as an open decision for Phase C,
  not resolving it in this proposal.
- `infrastructure/document/classification/` — could start as a rules-based classifier (filename +
  keyword heuristics, matching the project's existing preference for cheap heuristics over ML where
  sufficient — see `ReRanker`'s precedent) before any ML model is justified.
- `infrastructure/document/extraction/` — field-extraction adapter(s), likely one per `DocumentType`
  initially (a passport MRZ parser is a very different problem than free-text bank-statement extraction).

This keeps the same shape Sprint 4/5 already established: application defines the contract, infrastructure
provides the implementation, and swapping OCR/extraction vendors later never touches application or
domain code.

---

## 3. Form Schema / Field Mapping

### The generic form model (`domain/model`, country/visa-type agnostic)

```java
public record FormSchema(
        UUID id,
        String countryIso,
        String visaTypeCode,
        String formVersion,
        FormTargetType targetType,     // PDF_ACROFORM | WEB_FORM
        List<FormField> fields) {
}

public record FormField(
        String fieldKey,               // stable id within this schema, e.g. "applicant.passport.number"
        String label,
        FormFieldDataType dataType,     // TEXT, DATE, ENUM, ...
        boolean required) {
}

public record FieldMapping(
        UUID formSchemaId,
        String formFieldKey,
        String applicantProfilePath,    // e.g. "passportInfo.passportNumber" — dot-path into ApplicantProfile
        String transformationRule,      // e.g. "DATE_FORMAT:dd/MM/yyyy", "UPPERCASE", nullable = identity
        String validationRule) {        // e.g. "REGEX:^[A-Z][0-9]{7}$", nullable = none
}
```

`FormSchema` describes *what a form looks like* (independent of PDF-vs-web). `FieldMapping` describes
*how an `ApplicantProfile` fills it*. A `FormPreparationService` (application layer) resolves each
`FieldMapping` against a profile, applies the transformation rule, checks the validation rule, and
produces a `PreparedForm` result — a country/visa-type-specific *data* row (`FormSchema` + `FieldMapping`
rows in Postgres), never a country-specific Java class. Adding India's tourist-visa form or a future
country's business-visa form is a data-seeding exercise, not new code — matching `CLAUDE.md`'s "new
passport/destination pairs require only new documents and metadata, not new code" principle already
established for the RAG side.

`transformationRule`/`validationRule` as small DSL strings (not free Java code) keeps this data-driven
without embedding a scripting engine — a fixed, small vocabulary of rule types (`DATE_FORMAT`,
`UPPERCASE`, `REGEX`, `MAX_LENGTH`) covers the realistic cases and stays reviewable. If real-world forms
need more than that vocabulary can express, that's a signal to extend the vocabulary deliberately, not to
open up arbitrary code execution.

---

## 4. PDF Application Support

**PDFBox is used only when `FormSchema.targetType == PDF_ACROFORM`.** A `PdfFormFillingPort`
(`application/port`) implemented by `infrastructure/document/pdf/PdfAcroFormFiller` using PDFBox's
`PDAcroForm` API fills the actual PDF fields from a `PreparedForm`. This is the same pattern as every
other port/adapter pair in the codebase.

**Explicitly not assumed:** the Indian e-Visa/eFRRO-style online portals are **not** PDF forms — PDFBox
has no role there at all. `targetType` on `FormSchema` is the switch that keeps PDF logic from ever being
invoked against a web-form target. Before any real India `FormSchema` is seeded, its `targetType` needs to
be verified against what `indianvisaonline.gov.in` actually is (a web form, per Sprint 4's crawl notes) —
so India's first `FormSchema` will very likely be `WEB_FORM`, deferring PDFBox's first real use to
whichever country/visa-type genuinely has a downloadable PDF application form.

**New dependency required, not yet added:** `org.apache.pdfbox:pdfbox` is listed in `CLAUDE.md`'s tech
stack but is **not currently in `pom.xml`** — Tika bundles PDFBox transitively for *text extraction* only,
which is a different capability than `PDAcroForm` field-filling. Adding the direct dependency needs
explicit approval at the start of Phase E, per `DEVELOPMENT_RULES.md` §3, when there's an actual PDF
target to fill.

---

## 5. Web Form Assistant — separate future integration

Deliberately scoped as its own later phase (Phase F), not built alongside PDF support:

- A `WebFormAutofillPort` (application layer) with **no implementation in this phase**. Its eventual
  infrastructure adapter is out of scope for this proposal.
- **No automatic submission**, ever, without a human review-and-submit step in between.
- **No CAPTCHA/OTP/anti-bot bypass** — if a portal requires human verification, the assistant stops at
  "form is prepared and ready for you to submit," not "submitted for you." This is a hard boundary, not a
  performance target to optimize away later.
- Whatever future integration exists (browser-assisted autofill) only operates where the target portal's
  terms of service and applicable law permit automated form population — that determination happens
  per-portal, before any code targeting it is written, not assumed globally by this architecture.

The generic `FormSchema`/`FieldMapping` model from § 3 is designed so a web-form target is just a
different `targetType` value with the same data-driven mapping underneath — no separate domain model is
needed for web forms vs. PDF forms.

---

## 6. User Verification Workflow

Every `ExtractedField` starts `UNVERIFIED`. The rule, stated once and enforced everywhere:

> **No `ExtractedField` may be read by `FormPreparationService` (§3) unless its `verificationStatus` is
> `USER_CONFIRMED` or `USER_CORRECTED`.**

Concretely: passport number is OCR-extracted at 99% confidence → still `UNVERIFIED` → the user is shown
the extracted value and either confirms it (`USER_CONFIRMED`, `normalizedValue` unchanged) or edits it
(`USER_CORRECTED`, `normalizedValue` updated, `extractedValue` retained for audit). Confidence is a
*prioritization* signal for the UI (e.g. "review this one first, it's only 62% confident") — it is never a
threshold that lets a field skip verification. This mirrors the existing RAG pipeline's own posture:
`ConfidenceScorer` gates whether an *answer* is shown at all, but even a shown answer never bypasses
citation-back-to-source; here, even a high-confidence extraction never bypasses user confirmation.

A `ApplicantProfileVerificationService` (application layer) exposes the operations
(`confirmField`, `correctField`, `rejectField`) that transition `verificationStatus` — this is the one
place that mutates verification state, keeping the rule enforceable in one spot rather than scattered
across controllers.

---

## 7. Security / Privacy (proposed, intentionally not over-engineered)

Scoped to what's needed now, with explicit call-outs for what's deferred:

- **Encryption at rest:** sensitive document blobs (passport/NID/bank-statement scans) stored with
  column-level encryption or an encrypted object-store bucket — **not** plaintext blobs in Postgres.
  Exact mechanism (pgcrypto vs. envelope encryption via an external KMS) is a Phase C decision, not fixed
  here; either is a real option and the choice should be made against actual deployment target (self-hosted
  vs. managed cloud), not guessed in a proposal document.
- **Access control:** every `ApplicantDocument`/`ApplicantProfile` read requires ownership check against
  the (currently undecided, see §1) applicant identity. No cross-applicant read path exists anywhere in
  the API surface.
- **Retention/deletion:** `ApplicantDocument` gets a `retentionExpiresAt` timestamp set at upload; a
  scheduled job (same `@Scheduled` pattern as `SourceMonitoringConfig`) purges expired raw document blobs
  automatically. Extracted *fields* (already reviewed/confirmed by the user) may be retained longer than
  raw scans per policy — raw passport/NID images are the highest-sensitivity artifact and the ones with
  the shortest justified retention.
- **Audit trail:** every read of a raw document blob and every verification-state transition is logged,
  extending the existing `query_audit`-style append-only logging pattern already in the codebase (a new
  `applicant_document_access_log` table, same shape).
- **Minimal data exposure:** API responses never return raw document blobs inline — a signed,
  short-TTL retrieval URL/endpoint only, requested explicitly, not embedded in every profile fetch.
- **Explicitly deferred, not designed here:** encryption key rotation policy, field-level differential
  access (e.g. redacting passport number in UI contexts that don't need it), and formal compliance
  mapping (GDPR/local data-protection law) — these need their own dedicated review once there's a real
  deployment target, not speculative design now.

---

## 8. Database Schema Proposal (no migrations created yet)

Following the existing conventions exactly: `BIGSERIAL`/`UUID` PKs per existing table precedent
(reference tables like `countries`/`visa_types` use `BIGSERIAL`; document-shaped aggregates use
`UUID PRIMARY KEY DEFAULT gen_random_uuid()`), `snake_case` columns, `TIMESTAMPTZ` for instants.

```sql
-- applicant_profiles
CREATE TABLE applicant_profiles (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_id     UUID         NOT NULL,           -- opaque owner id, see §1 open question
    profile_json     JSONB        NOT NULL,           -- serialized ApplicantProfile value objects
    last_modified_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_applicant_profiles_applicant ON applicant_profiles(applicant_id);

-- applicant_documents (raw uploads: passport scan, NID, bank statement, photo, ...)
CREATE TABLE applicant_documents (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_profile_id  UUID         NOT NULL REFERENCES applicant_profiles(id) ON DELETE CASCADE,
    document_type         VARCHAR(50)  NOT NULL,      -- PASSPORT, NID, BANK_STATEMENT, PHOTO, ...
    storage_ref           TEXT         NOT NULL,       -- pointer to encrypted blob store, not the blob itself
    uploaded_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    retention_expires_at  TIMESTAMPTZ  NOT NULL,
    validation_status     VARCHAR(30)  NOT NULL DEFAULT 'PENDING' -- PENDING, VALID, INVALID, EXPIRED
);
CREATE INDEX idx_applicant_documents_profile ON applicant_documents(applicant_profile_id);

-- extracted_fields (one row per ExtractedField instance; verification_status lives here, not a
-- separate table — it's a property of the extraction, not an independent entity)
CREATE TABLE extracted_fields (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_profile_id  UUID         NOT NULL REFERENCES applicant_profiles(id) ON DELETE CASCADE,
    source_document_id    UUID         NOT NULL REFERENCES applicant_documents(id),
    field_path            VARCHAR(200) NOT NULL,       -- e.g. "passportInfo.passportNumber"
    extracted_value       TEXT         NOT NULL,
    normalized_value       TEXT         NOT NULL,
    confidence             NUMERIC(4,3) NOT NULL,
    verification_status   VARCHAR(20)  NOT NULL DEFAULT 'UNVERIFIED',
    verified_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_extracted_fields_profile ON extracted_fields(applicant_profile_id);

-- applicant_document_access_log (audit trail, append-only — mirrors query_audit's existing shape)
CREATE TABLE applicant_document_access_log (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_document_id UUID     NOT NULL REFERENCES applicant_documents(id),
    action            VARCHAR(30)  NOT NULL,           -- VIEWED, DOWNLOADED, VERIFIED, DELETED
    actor             VARCHAR(100),                    -- until real auth exists, may be null/system
    occurred_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_access_log_document ON applicant_document_access_log(applicant_document_id);

-- form_schemas (one row per country+visaType+targetType+version)
CREATE TABLE form_schemas (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    country_iso   CHAR(2)      NOT NULL,
    visa_type_id  BIGINT       NOT NULL REFERENCES visa_types(id),
    form_version  VARCHAR(20)  NOT NULL,
    target_type   VARCHAR(20)  NOT NULL,               -- PDF_ACROFORM, WEB_FORM
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (country_iso, visa_type_id, form_version)
);

-- form_fields (the target form's own field list — independent of any applicant)
CREATE TABLE form_fields (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    form_schema_id UUID         NOT NULL REFERENCES form_schemas(id) ON DELETE CASCADE,
    field_key      VARCHAR(200) NOT NULL,
    label          VARCHAR(200) NOT NULL,
    data_type      VARCHAR(30)  NOT NULL,
    required       BOOLEAN      NOT NULL DEFAULT false,
    UNIQUE (form_schema_id, field_key)
);

-- field_mappings (ApplicantProfile path -> form field, with transform/validation rules)
CREATE TABLE field_mappings (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    form_schema_id           UUID         NOT NULL REFERENCES form_schemas(id) ON DELETE CASCADE,
    form_field_key           VARCHAR(200) NOT NULL,
    applicant_profile_path   VARCHAR(200) NOT NULL,
    transformation_rule      VARCHAR(200),
    validation_rule          VARCHAR(200),
    FOREIGN KEY (form_schema_id, form_field_key) REFERENCES form_fields(form_schema_id, field_key)
);

-- applications (an applicant's actual in-progress/submitted application for one visa type)
CREATE TABLE applications (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_profile_id  UUID         NOT NULL REFERENCES applicant_profiles(id),
    form_schema_id        UUID         NOT NULL REFERENCES form_schemas(id),
    status                VARCHAR(30)  NOT NULL DEFAULT 'DRAFT', -- DRAFT, READY_FOR_REVIEW, PREPARED, SUBMITTED_MANUALLY
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- application_forms (the prepared/filled output for one application — a snapshot, not live-computed)
CREATE TABLE application_forms (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID         NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    prepared_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    output_ref      TEXT,                                -- pointer to filled PDF / prepared web-form payload
    validation_summary JSONB     NOT NULL                 -- which fields passed/failed validation, missing required fields
);
```

**Design notes:**
- `applicant_profiles.profile_json` stores the `ApplicantProfile` value objects as JSONB rather than one
  column per field — the profile shape (§1) is a fixed, known Java structure serialized/deserialized by
  the adapter, not a dynamic schema; JSONB avoids ~20 nullable columns for fields that don't apply to
  every visa type, while `extracted_fields` (a real relational table) is what verification workflows and
  audit queries actually need row-level access to.
- `verification_status` lives on `extracted_fields`, not a separate table, per the user's own § 8 wording
  ("verification status" listed alongside "extracted fields") — it's a column, not an independent entity,
  same reasoning as `documents.active` in the existing schema rather than a separate table tracking
  document lifecycle state.
- No migration files created — this is schema-as-proposal only, per the task instruction to wait for
  approval first.

---

## 9. Implementation Roadmap

### Phase A — Sprint 6 completion
**Status: was already done (commit `e7fc625`, 2026-08-19) before this session began** — `PROGRESS.md` just
hadn't been updated to reflect it; corrected today. No further work needed here.

### Phase B — Applicant Profile
- **Classes:** `domain/model/{ApplicantProfile, PersonalInfo, PassportInfo, NationalityInfo, AddressInfo,
  FamilyInfo, EmploymentInfo, TravelInfo, ExtractedField, VerificationStatus}`;
  `domain/repository/ApplicantProfileRepository`; `infrastructure/persistence/` JPA entity + adapter
  (JSONB mapping for `profile_json`); `application/applicant/` (new sub-package, needs the same
  "raise before creating" approval Sprint 7's `application/monitoring/` got) with
  `ApplicantProfileService`, `ApplicantProfileVerificationService`.
- **DB changes:** `applicant_profiles`, `extracted_fields` tables (§8), new Flyway `V10`/`V11`.
- **APIs:** `POST /api/v1/applicants/{id}/profile` (create/update), `GET .../profile`,
  `POST .../profile/fields/{fieldPath}/confirm`, `POST .../profile/fields/{fieldPath}/correct`.
- **Tests:** unit tests for `ApplicantProfileVerificationService`'s state transitions (the "never skip
  verification" rule from §6 is exactly the kind of invariant that needs a direct test); JPA integration
  test for JSONB round-trip, following the existing `DocumentRepositoryAdapterIntegrationTest` pattern.
- **Dependencies:** none new — this phase is pure domain/application/persistence, no OCR yet.
- **Risks:** the applicant-identity/ownership open question (§1) blocks real access control; a placeholder
  opaque `applicant_id` (no auth) is workable for this phase but should be flagged, not silently treated
  as final.

### Phase C — OCR + Document Intelligence
- **Classes:** `application/port/{OcrPort, DocumentClassificationPort, FieldExtractionPort,
  DocumentValidationPort}`; `application/document/DocumentIntelligenceService` (orchestrates upload →
  classify → extract → produce `ExtractedField` candidates); `infrastructure/document/ocr/`,
  `infrastructure/document/classification/`, `infrastructure/document/extraction/` adapters (concrete
  choice deferred, §2); `domain/model/ApplicantDocument`.
- **DB changes:** `applicant_documents`, `applicant_document_access_log` tables, new Flyway migration(s).
- **APIs:** `POST /api/v1/applicants/{id}/documents` (upload), `GET .../documents/{docId}` (signed
  retrieval, §7), `DELETE .../documents/{docId}`.
- **Tests:** unit tests per adapter (mocked OCR/classification responses); a document-upload-to-extracted-
  fields integration test.
- **Dependencies:** an OCR library/API — **not yet chosen, needs its own approval pass** before Phase C
  starts, given the cost/accuracy/privacy tradeoffs involved.
- **Risks:** OCR accuracy on real Bangladeshi passports/NIDs is unverified until tested against real
  (or realistic synthetic) samples; classification heuristics may misfire on scanned/photographed
  (non-flat, rotated) documents — start with a narrow, well-tested document set rather than assuming
  general robustness.

### Phase D — Visa Application model
- **Classes:** `domain/model/{Application, ApplicationStatus}`; `application/applicant/` gains
  `ChecklistService` (visa-type → required document list, reusing the existing RAG pipeline's retrieval —
  "what documents does a Tourist visa need" is itself answerable by the existing `VisaQueryService`
  against the knowledge base) and `ConsistencyValidationService` (cross-document field comparison, e.g.
  name spelling mismatch between passport and NID — surfaces conflicts, does not silently resolve them,
  same posture as the RAG pipeline's existing source-conflict handling).
- **DB changes:** `applications` table.
- **APIs:** `POST /api/v1/applicants/{id}/applications`, `GET .../applications/{appId}/checklist`,
  `GET .../applications/{appId}/consistency-check`.
- **Tests:** `ConsistencyValidationService` unit tests (mismatched-name case, missing-document case);
  `ChecklistService` tests against seeded knowledge-base content.
- **Dependencies:** none new.
- **Risks:** checklist accuracy is bounded by the existing knowledge base's coverage/currency — same
  known constraints already documented (Sprint 4/7 knowledge-base sourcing notes) apply here too.

### Phase E — PDF Form preparation
- **Classes:** `domain/model/{FormSchema, FormField, FieldMapping, PreparedForm}`;
  `application/port/PdfFormFillingPort`; `application/form/FormPreparationService`;
  `infrastructure/document/pdf/PdfAcroFormFiller` (PDFBox).
- **DB changes:** `form_schemas`, `form_fields`, `field_mappings`, `application_forms` tables.
- **APIs:** `POST /api/v1/applications/{appId}/prepare-form`, `GET .../form` (download prepared PDF).
- **Tests:** `FormPreparationService` unit tests (transformation/validation rule application, missing-
  required-field detection); a real PDFBox fill-and-read-back integration test against one seeded
  `FormSchema`.
- **Dependencies:** `org.apache.pdfbox:pdfbox` — **new dependency, needs explicit approval** (§4)
  before this phase starts.
- **Risks:** none until an actual PDF-target visa type/country is identified and confirmed to genuinely be
  an AcroForm (not a flat/non-fillable PDF, which would need a different, more complex approach entirely).

### Phase F — Web Form Assistant
- **Classes:** `application/port/WebFormAutofillPort` declared only — **no implementation** in this phase
  per §5's explicit boundary.
- **DB changes:** none beyond what Phase E already added (`targetType = WEB_FORM` reuses the same schema).
- **APIs:** none beyond what already exists — this phase is architectural placement only.
- **Tests:** none (no implementation).
- **Dependencies:** none decided.
- **Risks:** this is the phase most likely to run into legal/portal-ToS constraints; treat as "design the
  seam, implement later, per-portal, with explicit legal/technical clearance" rather than a committed
  build phase on this roadmap.

### Phase G — Travel Platform expansion
Out of scope for detailed planning now — `CLAUDE.md`'s existing Future Modules list (Destination RAG,
Airline Policy RAG, Hotel Policy RAG, Travel Planner Agent) already covers this; Phases B–E's ports
(`EmbeddingPort`, `VectorSearchPort`, `ChatPort`, the ingestion pipeline, and now `ApplicantProfile`) are
designed to be reused by those future modules without modification, consistent with `CLAUDE.md`'s
extensibility goal.

---

## Summary of what needs a decision before Phase B starts

1. Applicant identity/ownership model (§1) — opaque `applicant_id`, no auth, proposed as the Phase B
   default; confirm or redirect.
2. `application/applicant/` as a new approved sub-package (§9 Phase B) — same approval pattern as
   `application/monitoring/`.
3. Everything else in Phases C–F has its own phase-gated decision (OCR vendor, PDFBox dependency, web-form
   legal clearance) and does not need to be decided now.
