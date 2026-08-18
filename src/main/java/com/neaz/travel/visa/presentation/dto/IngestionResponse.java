package com.neaz.travel.visa.presentation.dto;

import java.util.UUID;

/**
 * Response body for {@code POST /api/v1/ingestion/documents}, wrapped in
 * {@code ApiResponse<IngestionResponse>}.
 *
 * @param documentId the id of the persisted (or previously existing) document
 * @param chunkCount the number of chunks stored for this document
 * @param status     {@code "CREATED"} or {@code "DUPLICATE"}
 */
public record IngestionResponse(UUID documentId, int chunkCount, String status) {
}