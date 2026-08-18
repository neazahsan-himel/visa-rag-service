package com.neaz.travel.visa.application.result;

import java.util.Objects;
import java.util.UUID;

/**
 * Outcome of the document ingestion use case, returned by
 * {@code DocumentIngestionService} to {@code IngestionController}.
 *
 * @param documentId the id of the persisted (or previously existing) document
 * @param chunkCount the number of chunks stored for this document
 * @param status     whether this document was newly created or already existed
 */
public record IngestionResult(UUID documentId, int chunkCount, Status status) {

    public IngestionResult {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (chunkCount < 0) {
            throw new IllegalArgumentException("chunkCount must not be negative");
        }
    }

    /**
     * Whether ingestion created a new document or found one already ingested
     * under the same file hash.
     */
    public enum Status {
        CREATED,
        DUPLICATE
    }
}