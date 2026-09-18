package com.neaz.travel.visa.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/applicants/{applicantId}/applications}.
 * {@code applicantId} itself comes from the URL path, not this body.
 *
 * @param passportIso    ISO country code of the applicant's passport
 * @param destinationIso ISO country code of the visa-issuing destination
 * @param visaTypeCode   visa type code, e.g. {@code "TOURIST"}
 */
public record CreateApplicationRequest(
        @NotBlank String passportIso, @NotBlank String destinationIso, @NotBlank String visaTypeCode) {
}
