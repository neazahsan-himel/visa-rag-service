package com.neaz.travel.visa.domain.model;

/**
 * Read-only view of an {@link ApplicantProfile}'s nationality fields,
 * assembled from its internal field map. Any component is {@code null} if
 * that field hasn't been set on the profile yet.
 *
 * @param nationality         current nationality (ISO country code)
 * @param previousNationality prior nationality, if any; {@code null} if not applicable
 */
public record NationalityInfo(
        ExtractedField<String> nationality,
        ExtractedField<String> previousNationality) {
}
