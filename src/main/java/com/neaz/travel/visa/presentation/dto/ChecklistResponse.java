package com.neaz.travel.visa.presentation.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response body for {@code GET
 * /api/v1/applicants/{applicantId}/applications/{applicationId}/checklist},
 * wrapped in {@code ApiResponse<ChecklistResponse>}. Mirrors {@code
 * ChecklistResult}'s shape.
 */
public record ChecklistResponse(UUID applicationId, String checklistText, List<CitationDto> citations, double confidenceScore) {

    /** Wire shape of {@code Citation}. */
    public record CitationDto(String ref, String source, String url, LocalDate lastUpdated, UUID chunkId) {
    }
}
