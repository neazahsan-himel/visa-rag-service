package com.neaz.travel.visa.domain.model;

import java.time.LocalDate;

/**
 * Read-only view of an {@link ApplicantProfile}'s travel-intent fields,
 * assembled from its internal field map. Any {@link ExtractedField}
 * component is {@code null} if that field hasn't been set on the profile
 * yet.
 *
 * @param purposeOfVisit        free text the applicant enters directly; no source document to extract from, so unwrapped
 * @param intendedArrivalDate   planned arrival date
 * @param intendedDurationDays  planned length of stay, in days
 * @param portOfEntry           planned port of entry; {@code null} if not yet known
 */
public record TravelInfo(
        String purposeOfVisit,
        ExtractedField<LocalDate> intendedArrivalDate,
        ExtractedField<Integer> intendedDurationDays,
        ExtractedField<String> portOfEntry) {
}
