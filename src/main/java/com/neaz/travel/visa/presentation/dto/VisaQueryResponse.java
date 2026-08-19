package com.neaz.travel.visa.presentation.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response body for {@code POST /api/v1/visa/query}, wrapped in
 * {@code ApiResponse<VisaQueryResponse>}.
 *
 * @param answer          the generated answer text
 * @param citations       citations backing every factual statement in the answer
 * @param confidenceScore score in {@code [0.0, 1.0]}
 */
public record VisaQueryResponse(String answer, List<CitationDto> citations, double confidenceScore) {

    /**
     * Wire shape of {@code Citation}.
     */
    public record CitationDto(String ref, String source, String url, LocalDate lastUpdated, UUID chunkId) {
    }
}