package com.neaz.travel.visa.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/visa/query}.
 *
 * @param query       the natural-language question text
 * @param passport    ISO country code of the applicant's passport (e.g. "BD")
 * @param destination ISO country code of the visa-issuing destination (e.g. "IN")
 * @param visaType    visa type code to narrow the search (e.g. "TOURIST");
 *                    {@code null} means all visa types
 */
public record VisaQueryRequest(
        @NotBlank String query,
        @NotBlank String passport,
        @NotBlank String destination,
        String visaType) {
}