package com.neaz.travel.visa.domain.repository;

import com.neaz.travel.visa.domain.model.SourceCheckLog;

import java.util.List;
import java.util.UUID;

/**
 * Repository contract for {@link SourceCheckLog} rows, implemented by
 * {@code infrastructure/persistence} against the {@code source_check_log}
 * table.
 */
public interface SourceCheckLogRepository {

    /**
     * Records the outcome of checking one document's official source.
     *
     * @param documentId   the document that was checked
     * @param detectedHash hash of the live source's extracted text; must be
     *                     {@code null} when {@code status} is {@code FETCH_FAILED}
     * @param status       the result of the check
     */
    void log(UUID documentId, String detectedHash, SourceCheckLog.Status status);

    /**
     * Finds every check result flagged {@code CHANGED} that a curator has
     * not yet reviewed.
     *
     * @return pending check results, implementation-defined order
     */
    List<SourceCheckLog> findPendingReview();

    /**
     * Marks a check result as reviewed by a curator.
     *
     * @param id identifier of the check result to mark
     */
    void markReviewed(UUID id);
}
