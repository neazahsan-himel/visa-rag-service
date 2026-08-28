package com.neaz.travel.visa.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The outcome of checking one ingested {@link Document}'s official source
 * for changes since it was last ingested. Written by the source-change
 * monitor; read back by the pending-changes review workflow.
 *
 * @param id           identifier of this check result
 * @param documentId   the {@link Document} that was checked
 * @param checkedAt    when the check ran
 * @param detectedHash hash of the live source's extracted text; {@code null} when {@code status} is {@code FETCH_FAILED}
 * @param status       the result of the check
 * @param reviewed     whether a curator has acted on a {@code CHANGED} result
 */
public record SourceCheckLog(
        UUID id,
        UUID documentId,
        Instant checkedAt,
        String detectedHash,
        Status status,
        boolean reviewed) {

    public SourceCheckLog {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(checkedAt, "checkedAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (status == Status.FETCH_FAILED && detectedHash != null) {
            throw new IllegalArgumentException("detectedHash must be null when status is FETCH_FAILED");
        }
        if (status != Status.FETCH_FAILED && (detectedHash == null || detectedHash.isBlank())) {
            throw new IllegalArgumentException("detectedHash must not be blank unless status is FETCH_FAILED");
        }
    }

    /**
     * Whether the live source matched, differed from, or could not be
     * compared against the stored document at the time of the check.
     */
    public enum Status {
        UNCHANGED,
        CHANGED,
        FETCH_FAILED
    }
}
