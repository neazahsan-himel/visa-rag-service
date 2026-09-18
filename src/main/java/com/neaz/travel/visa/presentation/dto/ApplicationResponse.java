package com.neaz.travel.visa.presentation.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for the application endpoints, wrapped in
 * {@code ApiResponse<ApplicationResponse>}. Mirrors {@code
 * ApplicationResult}'s shape without referencing any domain type directly.
 */
public record ApplicationResponse(
        UUID id,
        UUID applicantId,
        String passportIso,
        String destinationIso,
        String visaTypeCode,
        String status,
        Instant createdAt) {
}
