package com.neaz.travel.visa.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Query parameters for {@code GET /api/v1/visa/fees}, bound with
 * {@code @ModelAttribute} so missing or malformed values are rejected by bean
 * validation (HTTP 400) rather than reaching the application layer.
 *
 * <p>Unlike {@link VisaQueryRequest}, {@code visaType} is required: a fee is
 * always looked up for one visa type. Callers wanting the flat fee that
 * applies to every type pass {@code GENERAL}.
 *
 * @param passport    ISO 3166-1 alpha-2 code of the applicant's passport (e.g. "BD")
 * @param destination ISO 3166-1 alpha-2 code of the visa-issuing destination (e.g. "IN")
 * @param visaType    visa type code (e.g. "TOURIST", "DOUBLE_ENTRY", "GENERAL")
 */
public record VisaFeeRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be a two-letter uppercase ISO country code")
        String passport,
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be a two-letter uppercase ISO country code")
        String destination,
        @NotBlank @Pattern(regexp = "[A-Z_]+", message = "must be an uppercase visa type code")
        String visaType) {
}
