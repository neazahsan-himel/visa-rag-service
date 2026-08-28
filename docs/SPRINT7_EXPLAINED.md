# Sprint 7 — What Was Built and Why

This document explains every file created or changed in Sprint 7 in plain language — code level, architecture level, and business/domain level. Use it as a reference when reviewing the code, resuming work later, or explaining the project in interviews.

---

## Why Sprint 7 Comes Seventh

Sprint 6 closed out error handling, tests, and docs — at that point the pipeline was genuinely production-shaped for a *static* knowledge base. But visa rules aren't static: embassy fee schedules and document checklists change at the source without any warning. Asked directly what happens in that case, the honest answer surfaced two real gaps, not one:

1. **Nothing detects a source change.** No crawler, no scheduler — a stale document just sits there, cited as if current, forever.
2. **Even a manual fix was broken.** Re-ingesting updated content for the same source created a *second* `active` `Document` alongside the first — `DocumentIngestionService`, `DocumentRepositoryAdapter`, and `QdrantVectorStoreAdapter` were all checked, and none of them ever called `Document.deactivate()`. Old and new versions would become simultaneously retrievable, risking contradictory content landing in the same LLM prompt — a worse outcome than simply being stale.

Sprint 7 closes both, deliberately scoped as **detect + flag only, no automatic re-ingestion** (confirmed via `AskUserQuestion`, 2026-08-18) — Sprint 4 had already caught `WebFetch`'s summarization model silently mangling a table once, so auto-writing unreviewed re-fetched content into what the system treats as authoritative would be a real regression against the no-fabrication/curated-sources rule. A human stays in the loop for every content change; only *detecting* and *superseding* are automated.

```
SourceChangeMonitor (scheduled)
  → UrlDocumentLoader → TikaDocumentParser → Sha256Hasher → compare to Document.fileHash
      → SourceCheckLogRepository.log(UNCHANGED | CHANGED | FETCH_FAILED)

Curator: GET /api/v1/ingestion/pending-changes → PendingChangeQueryService

Curator: POST /api/v1/ingestion/documents (existing endpoint, re-ingest)
  → DocumentIngestionService detects existing active version for the same sourceUrl
      → deactivates it in PostgreSQL (Document.deactivate()) AND Qdrant (payload-only update)
```

---

## `infrastructure/document/UrlDocumentLoader.java`

Fetches a document's live bytes over HTTP(S), mirroring `FileSystemDocumentLoader`'s narrow "just get me the bytes" responsibility — no parsing logic here.

### Why JDK `HttpClient`, not a new dependency

`DEVELOPMENT_RULES.md` §3 requires stating dependency coordinates and justification before touching `pom.xml`. `java.net.http.HttpClient` is standard library since Java 11 — no new dependency needed at all, which is strictly better than justifying one.

### Fails closed, on purpose

The sprint's explicit requirement: never misreport a fetch failure as a content comparison result. Any `IOException`, `InterruptedException`, timeout, or non-2xx HTTP status throws `VisaRagException` — there's no code path that could return a partial or empty body as if it were valid content. This matters downstream: `SourceChangeMonitor` treats "the fetch itself failed" (`FETCH_FAILED`) as a completely different, non-actionable outcome from "the fetch succeeded and the content differs" (`CHANGED`). A loader that swallowed errors and returned empty bytes would silently turn every unreachable source into a false `CHANGED` (empty hash mismatches the real one).

A `User-Agent` header is set explicitly — some government sites reject the default Java HTTP client UA outright, which would otherwise show up as a generic, confusing I/O failure rather than a real content check.

---

## Wiring `UrlDocumentLoader` into `TikaDocumentParser`

This is the piece that makes the loader actually reachable from the application layer, and it required a genuine design choice, resolved via `AskUserQuestion`.

### The fork: reuse `DocumentParsingPort`, or add a parallel port for monitoring?

Two designs were on the table. One: give `SourceChangeMonitor` its own dedicated port, implemented by a new infra class that independently composes `UrlDocumentLoader` + Tika — fully isolated from ingestion's parsing pathway. Two: extend `TikaDocumentParser` to dispatch on whether `DocumentSource.location()` looks like a URL (`http://`/`https://` prefix) or a filesystem path, and let `SourceChangeMonitor` reuse the *existing* `DocumentParsingPort` — the same interface `DocumentIngestionService` already depends on.

The user picked reuse, and `DocumentSource`'s own pre-existing Javadoc practically predicted this: it already said "**currently** always a filesystem path" — a hint the original Sprint 4 author left that this type was expected to grow a second location kind eventually. Extending it kept `DocumentParsingPort` as the single "give me text from wherever this document lives" entry point for both ingestion and monitoring, at the cost of a small, contained change to an already-shipped Sprint 4 class (one new constructor dependency, one `isUrl()` dispatch check).

---

## `domain/model/SourceCheckLog.java` and `domain/repository/SourceCheckLogRepository.java`

The sprint plan's task list said "JPA entity/repository" for this piece — in practice it expanded to a full domain-layer type + repository interface first, because `SourceChangeMonitor` lives in the *application* layer and this project's Clean Architecture rule is absolute: application code never depends on JPA classes directly. This mirrors the exact shape `VisaQueryLogRepository`/`QueryAuditJpaEntity` already established in Sprint 2/3 — flagged to the user as a scope expansion before building, per this project's standing "surface deviations explicitly" convention.

`SourceCheckLog` is a record, not a mutable class like `Document` — nothing needs an in-memory "mark reviewed" transition; the review endpoint does a direct targeted database update instead. Its constructor enforces one real invariant: `detectedHash` must be present for `UNCHANGED`/`CHANGED` and must be `null` for `FETCH_FAILED` — a fetch failure has no content to hash, and catching a violation of that at construction time (not just trusting callers) keeps `UrlDocumentLoader`'s fail-closed guarantee from being silently undermined by a bad caller somewhere downstream.

`SourceCheckLogRepository` has three methods: `log(documentId, detectedHash, status)` (write path, mirrors `VisaQueryLogRepository.log`'s primitive-params style — the DB generates the id, nothing needs it back), `findPendingReview()` (`CHANGED` + unreviewed, backing the review endpoint), and `markReviewed(id)` (a targeted update, no fetch-mutate-save round trip needed for a single boolean flip).

---

## `V9__create_source_check_log.sql` + JPA entity/repository/adapter

Standard Flyway migration following `query_audit`'s established shape: UUID PK via `gen_random_uuid()`, a `CHECK` constraint pinning `status` to exactly `UNCHANGED`/`CHANGED`/`FETCH_FAILED`, and two indexes — `(document_id, checked_at DESC)` for "latest check per document," and a **partial** index on `(status, reviewed) WHERE status = 'CHANGED' AND reviewed = false`, which is exactly the query the review endpoint runs.

`SourceCheckLogJpaEntity` gets a `@GeneratedValue` id (same rationale as `QueryAuditJpaEntity` — the write path never needs it back) and is the one entity in the codebase with a real mutator, `setReviewed(boolean)` — every other field is write-once. This is the first concrete precedent in this codebase for "how does a mutable-flag update actually get persisted through JPA" — a question that resolves itself simply here (`findById` → `setReviewed(true)` → `save`) but is the same shape `Document.active` will eventually need for anything beyond the ingestion-time deactivation this sprint already wires up.

---

## `application/monitoring/SourceChangeMonitor.java` — A New Package, Approved First

`application/monitoring/` isn't in `CLAUDE.md`'s original package list (`application/{query, ingestion, command, result, port}`). Per `CLAUDE.md`'s own rule — "if a new package seems necessary, raise it as a question before creating it" — this was raised via `AskUserQuestion` before any code was written, offering the alternative of folding monitoring into `application/ingestion/`. The user chose the new package: source-change detection is a distinct concern from ingesting a document, even though both eventually touch the same `Document` aggregate.

### Two prerequisite `DocumentRepository` methods, not in the original plan

`SourceChangeMonitor` needs to enumerate "every active document with a source URL" — `DocumentRepository` had no listing method at all before this sprint, only `findById`/`findByFileHash`. `findAllActiveWithSourceUrl()` was added, reusing the same chunk-reconstructing `toDomain` mapper `findById` already uses. This *is* mildly wasteful — full `Document` reconstruction (including chunks) for a query that only needs three fields (id, `sourceUrl`, `fileHash`) — but this runs on a schedule against a small, curated knowledge base, not a request-latency path, so a dedicated lightweight projection type wasn't worth the extra abstraction at this project's current scale.

### The actual check logic

Per document: `documentParsingPort.parse(new DocumentSource(sourceUrl))` → `Sha256Hasher.hex(text)` → compare to `document.getFileHash()`. Equal hashes → `UNCHANGED`; different → `CHANGED`; any `VisaRagException` from the parse (everything `UrlDocumentLoader` fails closed on, plus any Tika parse failure) → caught and logged as `FETCH_FAILED` with a `null` hash. A failure checking one document never aborts the batch — `checkAll()` iterates every document independently.

No logger in this class, matching `DocumentIngestionService`'s existing convention of no logging in application-layer services — the persisted `source_check_log` row *is* the audit trail; that's its entire purpose.

---

## `common/util/Sha256Hasher.java` — Extracted Mid-Sprint

Both `DocumentIngestionService.fileHash` (Sprint 4) and `SourceChangeMonitor.detectedHash` (this sprint) need to hash extracted text with SHA-256, hex-encoded — and critically, they need to produce **byte-identical** output for identical input, or the whole comparison this sprint exists to make is meaningless. `DocumentIngestionService` already had this logic as a private method; duplicating it into `SourceChangeMonitor` would have created two independently-maintained copies that could silently drift apart (e.g. one gets touched for an unrelated reason, the other doesn't, and suddenly `UNCHANGED` stops meaning what it says).

`common/util/` was an empty, reserved package slot in `CLAUDE.md`'s own structure — exactly the kind of shared, stateless helper `CLAUDE.md` allows as an exception to its "avoid static utility classes" rule ("unless necessary"). `DocumentIngestionService` was refactored to call the extracted `Sha256Hasher.hex(...)` too, so there is now exactly one hashing implementation in the codebase.

---

## Scheduling: `config/SourceMonitoringConfig.java`

`@Configuration` + `@EnableScheduling`, with one `@Scheduled` method that does nothing but call `sourceChangeMonitor.checkAll()` — the config layer stays free of business logic, matching `WebConfig`'s existing style exactly. The interval is configurable (`source-monitoring.check-interval-ms`, default 24 hours) rather than hardcoded, since how often it's worth re-checking slow-moving embassy documents is an operational decision, not a code one.

---

## `GET /api/v1/ingestion/pending-changes` — The Review Endpoint

This also expanded beyond its one-line task description once curator usability was considered: a raw `List<SourceCheckLog>` would show a curator a UUID and a hash with no way to recognize *which document* changed. Two new pieces close that gap:

- `application/result/PendingChangeResult` — an application-layer result record enriched with the document's title (and `sourceUrl`), mirroring `IngestionResult`/`VisaQueryResult`'s role.
- `application/monitoring/PendingChangeQueryService` — deliberately **separate** from `SourceChangeMonitor` (querying results for review is a different responsibility from running checks, per single-responsibility). For each pending log row, resolves the parent document's title via `DocumentRepository.findById`, throwing `IllegalStateException` if that document is somehow missing — matching `DocumentRepositoryAdapter`'s existing "referenced X no longer exists" convention for invariant violations. Documents are only ever deactivated in this codebase, never deleted, so a missing document behind a `source_check_log` row would be a genuine data-integrity bug, not a normal case to handle gracefully.

`IngestionController` gained a `GET /pending-changes` handler and `IngestionMapper` gained a matching `toResponse(PendingChangeResult)` overload — same shape as the existing ingestion endpoint pair, no new controller class needed since both endpoints concern curator-facing ingestion workflows.

**Worth knowing:** there is currently no HTTP endpoint for `markReviewed` — the domain repository method exists (needed for the read/write contract to be complete), but nothing calls it yet. A curator's only way to act on a flagged change today is to re-ingest, which is the actual point of the workflow; marking something reviewed *without* re-ingesting has no UI surface yet.

---

## Version Supersession in `DocumentIngestionService`

The second gap from the opening question, now actually fixed. Right after the existing dedup check (identical content → `DUPLICATE`, unchanged from Sprint 4) and *before* building the new document, the service looks up any currently-active document for the same `sourceUrl` via a second new `DocumentRepository` method, `findActiveBySourceUrl`.

### Why the lookup happens before saving the new document, not after

If the lookup ran *after* the new document was already saved, it could ambiguously match the *new* document instead of the old one — both would be active, with the same `sourceUrl`, at that point. Looking up the prior version first, while it's still the only active document for that source, sidesteps the ambiguity entirely rather than requiring an "exclude the new id" filter.

### What actually happens on supersession

Once the new document and its chunks/vectors are saved (unchanged Sprint 4 flow), if a previous version was found: `previous.deactivate()` (an existing `Document` method from day one, never previously called by anything) → `documentRepository.save(previous)` persists `active = false` in PostgreSQL → `vectorSearchPort.deactivateDocument(previous.getId())` does the equivalent in Qdrant (see below). Both stores are updated in the same code path, closing the "old and new simultaneously retrievable" gap in one place.

---

## Propagating `active` into Qdrant

`QdrantVectorStoreAdapter` had **zero** concept of active/inactive before this sprint — every point, forever, was a search candidate. Three changes, each verified against the real Qdrant Java client before being written, continuing the `javap`-against-jar discipline established in Sprint 5 for `search()`.

### 1. `upsert()` now writes `active: true`

One new payload field, `ValueFactory.value(true)`, on every chunk written at ingestion time. A freshly-ingested chunk is always active by definition; it only becomes inactive later, exclusively through deactivation.

### 2. `search()` now filters on `active: true`

One new `must` condition, `ConditionFactory.match("active", true)` — a boolean match, distinct from the string-based `matchKeyword` already used for the ISO-code filters. **A caveat worth being explicit about:** any point upserted *before* this sprint has no `active` field at all, so it silently fails to match this new condition and becomes unretrievable until re-ingested. Acceptable for a project at this pre-production stage with a small, easily re-ingestible knowledge base — but a real concern if this code were ever pointed at an already-populated, live collection without a migration step.

### 3. `deactivateDocument(UUID documentId)` — a payload-only update, no re-embedding

This is the new method on `VectorSearchPort`, and the one requiring real API verification: Qdrant's Java client exposes `setPayloadAsync(SetPayloadPoints, Duration)`, where `SetPayloadPoints` carries a `PointsSelector` that can wrap either explicit point ids or a `Filter` — confirmed via `javap -classpath client-1.9.1.jar io.qdrant.client.QdrantClient` before writing any code, the same discipline Sprint 5's `search()` implementation used. The implementation builds a `Filter` matching `document_id` (the same string-keyword condition already used elsewhere), wraps it in a `PointsSelector`, and calls `setPayloadAsync` with `{"active": false}` — every chunk belonging to that document gets its payload flipped in one gRPC call, with no vector re-upsert and no re-embedding needed. This is exactly the "payload-only update path" the sprint plan called for.

---

## A Test Broken and Fixed: `RagPipelineIntegrationTest`

Adding a third constructor parameter to `IngestionController` (`PendingChangeQueryService`, for the new review endpoint) broke `RagPipelineIntegrationTest`, which constructs `IngestionController` manually rather than through Spring's dependency injection (it's a `@DataJpaTest` with hand-wired application services, not a full `@SpringBootTest`). This surfaced as a `NoSuchMethodError` at test runtime, not a compile error — Maven's incremental compiler didn't recompile the test on the first `mvn test` run after the constructor changed, and the stale bytecode expected the old two-argument constructor. `mvn clean test` reproduced it as a real, deterministic failure. Fixed by adding `SourceCheckLogRepositoryAdapter` to the test's `@Import` list, autowiring it, constructing a real `PendingChangeQueryService` in `setUp()`, and passing it through — the same pattern the test already used for every other hand-wired service. All 45 tests pass after the fix.

**Lesson for future sessions:** after changing a constructor signature on a class any test constructs manually (not via `@SpringBootTest`/full DI), run `mvn clean test`, not just `mvn test` — incremental compilation can hide exactly this class of break behind a stale `.class` file.

---

## Files Created / Changed in Sprint 7

```
src/main/java/com/neaz/travel/visa/infrastructure/document/
  UrlDocumentLoader.java                                        ← new
  TikaDocumentParser.java                                       ← dispatches on http(s):// vs filesystem path
src/main/java/com/neaz/travel/visa/domain/model/
  DocumentSource.java                                           ← Javadoc updated (URL support)
  SourceCheckLog.java                                           ← new
src/main/java/com/neaz/travel/visa/domain/repository/
  SourceCheckLogRepository.java                                 ← new
  DocumentRepository.java                                       ← +findAllActiveWithSourceUrl, +findActiveBySourceUrl
src/main/resources/db/migration/
  V9__create_source_check_log.sql                                ← new
src/main/java/com/neaz/travel/visa/infrastructure/persistence/
  SourceCheckLogJpaEntity.java                                  ← new
  SourceCheckLogJpaRepository.java                               ← new
  SourceCheckLogRepositoryAdapter.java                           ← new
  DocumentJpaRepository.java                                    ← +2 derived queries
  DocumentRepositoryAdapter.java                                ← +2 methods
src/main/java/com/neaz/travel/visa/common/util/
  Sha256Hasher.java                                              ← new
src/main/java/com/neaz/travel/visa/application/ingestion/
  DocumentIngestionService.java                                  ← uses Sha256Hasher; version supersession wired in
src/main/java/com/neaz/travel/visa/application/monitoring/
  SourceChangeMonitor.java                                       ← new package, new class
  PendingChangeQueryService.java                                 ← new
src/main/java/com/neaz/travel/visa/application/result/
  PendingChangeResult.java                                       ← new
src/main/java/com/neaz/travel/visa/application/port/
  VectorSearchPort.java                                          ← +deactivateDocument
src/main/java/com/neaz/travel/visa/infrastructure/vectorstore/
  QdrantVectorStoreAdapter.java                                  ← active payload field, active filter, deactivateDocument()
src/main/java/com/neaz/travel/visa/config/
  SourceMonitoringConfig.java                                    ← new
src/main/java/com/neaz/travel/visa/presentation/dto/
  PendingChangeResponse.java                                     ← new
src/main/java/com/neaz/travel/visa/presentation/mapper/
  IngestionMapper.java                                           ← +toResponse(PendingChangeResult)
src/main/java/com/neaz/travel/visa/presentation/controller/
  IngestionController.java                                      ← +GET /pending-changes
src/main/resources/
  application.properties                                        ← +2 source-monitoring properties
src/test/java/com/neaz/travel/visa/
  RagPipelineIntegrationTest.java                                ← fixed for the new IngestionController constructor
docs/
  api.md                                                          ← +pending-changes endpoint, +Source Change Monitoring section
README.md                                                         ← +review-endpoint curl example, +2 config properties
SPRINTS.md, PROGRESS.md                                           ← Sprint 7 marked done
```

---

## How to Verify Sprint 7 Works

```bash
docker version   # Testcontainers needs a running Docker daemon
mvn clean test
```

**Expected: `Tests run: 45, Failures: 0, Errors: 0, Skipped: 0`** — verified during the sprint against real, ephemeral PostgreSQL and Qdrant containers, including `RagPipelineIntegrationTest` (now wired for the new `IngestionController` constructor) and `DocumentRepositoryAdapterIntegrationTest`.

**Not yet run — an actual live-source check**, which needs a real reachable URL and a running stack:

```bash
docker compose up
export OPENAI_API_KEY=...
./mvnw spring-boot:run

# Ingest something with a real sourceUrl, wait for (or manually trigger) a
# scheduled check, then:
curl http://localhost:8081/api/v1/ingestion/pending-changes

# Re-ingest the same source with different content to see supersession:
curl -X POST http://localhost:8081/api/v1/ingestion/documents \
  -H "Content-Type: application/json" \
  -d @path/to/updated-metadata.json

# Expected: the prior document's `active` flag flips to false in PostgreSQL,
# and its Qdrant points stop appearing in query results — verify by querying
# the same passport/destination/visaType and confirming only the new
# document's content is cited.
```

This hasn't been run against a live stack with a real government URL in the session that wrote the code — the known session/form-driven-portal constraint (`indianvisaonline.gov.in`, `indianvisa-bangladesh.nic.in`) means even a real run would likely need a static PDF source to demonstrate a clean `UNCHANGED`/`CHANGED` result rather than `FETCH_FAILED`. Running it locally against the HCI Dhaka PDF source is the real verification step.
