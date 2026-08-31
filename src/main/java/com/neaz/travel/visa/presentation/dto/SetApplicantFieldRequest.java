package com.neaz.travel.visa.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/v1/applicants/{applicantId}/profile/fields}.
 * {@code applicantId} itself comes from the URL path, not this body.
 *
 * @param fieldPath one of {@code ApplicantProfile}'s {@code PATH_*} constants
 * @param value     the value to set — a plain string, number, or (for an
 *                  address field) a nested object with line1/line2/city/postalCode/countryIso
 */
public record SetApplicantFieldRequest(@NotBlank String fieldPath, @NotNull Object value) {
}
