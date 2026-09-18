package com.neaz.travel.visa.presentation.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response body for {@code GET
 * /api/v1/applicants/{applicantId}/applications/{applicationId}/consistency-check},
 * wrapped in {@code ApiResponse<ConsistencyCheckResponse>}. Mirrors {@code
 * ConsistencyCheckResult}'s shape.
 */
public record ConsistencyCheckResponse(UUID applicantId, boolean consistent, List<FieldConflictDto> conflicts) {

    /** Wire shape of {@code ConsistencyCheckResult.FieldConflict}, exposing each reading's normalized value only. */
    public record FieldConflictDto(String fieldPath, Object mrzValue, Object visualValue) {
    }
}
