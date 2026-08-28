package com.neaz.travel.visa.application.result;

import com.neaz.travel.visa.domain.model.SourceCheckLog;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One unreviewed {@code CHANGED} source-check result, enriched with the
 * document's title so a curator can recognize which document it refers to
 * without looking up the id separately.
 *
 * @param checkId       identifier of the {@link SourceCheckLog} row
 * @param documentId    the document that was checked
 * @param documentTitle the document's title
 * @param sourceUrl     the official source URL that was checked
 * @param checkedAt     when the check ran
 * @param detectedHash  hash of the live source's extracted text
 */
public record PendingChangeResult(
        UUID checkId,
        UUID documentId,
        String documentTitle,
        String sourceUrl,
        Instant checkedAt,
        String detectedHash) {

    public PendingChangeResult {
        Objects.requireNonNull(checkId, "checkId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(documentTitle, "documentTitle must not be null");
        Objects.requireNonNull(sourceUrl, "sourceUrl must not be null");
        Objects.requireNonNull(checkedAt, "checkedAt must not be null");
        Objects.requireNonNull(detectedHash, "detectedHash must not be null");
    }
}
