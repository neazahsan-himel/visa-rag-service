package com.neaz.travel.visa.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/v1/applicants/{applicantId}/profile/fields/verify}.
 *
 * @param fieldPath      one of {@code ApplicantProfile}'s {@code PATH_*} constants
 * @param action         {@code "CONFIRM"}, {@code "CORRECT"}, or {@code "REJECT"}
 * @param correctedValue required when {@code action} is {@code "CORRECT"}; omitted otherwise
 */
public record VerifyApplicantFieldRequest(
        @NotBlank String fieldPath,
        @NotBlank @Pattern(regexp = "CONFIRM|CORRECT|REJECT") String action,
        Object correctedValue) {
}
