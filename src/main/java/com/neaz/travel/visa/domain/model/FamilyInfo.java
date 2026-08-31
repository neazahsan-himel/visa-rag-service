package com.neaz.travel.visa.domain.model;

/**
 * Read-only view of an {@link ApplicantProfile}'s family fields, assembled
 * from its internal field map. Any component is {@code null} if that field
 * hasn't been set on the profile yet.
 *
 * @param fatherName    the applicant's father's name
 * @param motherName    the applicant's mother's name
 * @param spouseName    the applicant's spouse's name; {@code null} if not applicable
 * @param maritalStatus e.g. "SINGLE", "MARRIED" — open-ended, not a fixed enum
 */
public record FamilyInfo(
        ExtractedField<String> fatherName,
        ExtractedField<String> motherName,
        ExtractedField<String> spouseName,
        ExtractedField<String> maritalStatus) {
}
