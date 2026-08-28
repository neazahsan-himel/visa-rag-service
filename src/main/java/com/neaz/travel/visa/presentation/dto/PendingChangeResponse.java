package com.neaz.travel.visa.presentation.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One item in the response body for {@code GET /api/v1/ingestion/pending-changes},
 * wrapped in {@code ApiResponse<List<PendingChangeResponse>>}.
 *
 * @param checkId       identifier of the underlying source-check result
 * @param documentId    the document that changed
 * @param documentTitle the document's title
 * @param sourceUrl     the official source URL that was checked
 * @param checkedAt     when the change was detected
 * @param detectedHash  hash of the live source's extracted text
 */
public record PendingChangeResponse(
        UUID checkId,
        UUID documentId,
        String documentTitle,
        String sourceUrl,
        Instant checkedAt,
        String detectedHash) {
}
