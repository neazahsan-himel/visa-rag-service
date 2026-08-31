package com.neaz.travel.visa.domain.model;

import java.time.LocalDate;

/**
 * Read-only view of an {@link ApplicantProfile}'s personal-information
 * fields, assembled from its internal field map. Any component is
 * {@code null} if that field hasn't been set on the profile yet.
 *
 * @param fullName      the applicant's full legal name
 * @param dateOfBirth   date of birth
 * @param gender        as stated by the applicant/source document
 * @param placeOfBirth  place of birth
 */
public record PersonalInfo(
        ExtractedField<String> fullName,
        ExtractedField<LocalDate> dateOfBirth,
        ExtractedField<String> gender,
        ExtractedField<String> placeOfBirth) {
}
