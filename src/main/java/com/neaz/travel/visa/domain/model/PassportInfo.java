package com.neaz.travel.visa.domain.model;

import java.time.LocalDate;

/**
 * Read-only view of an {@link ApplicantProfile}'s passport fields, assembled
 * from its internal field map. Any component is {@code null} if that field
 * hasn't been set on the profile yet.
 *
 * @param passportNumber the passport number
 * @param issuingCountry ISO country code of the issuing authority
 * @param issueDate      date the passport was issued
 * @param expiryDate     date the passport expires
 * @param passportType   e.g. "ORDINARY", "DIPLOMATIC" — open-ended, not a fixed enum
 */
public record PassportInfo(
        ExtractedField<String> passportNumber,
        ExtractedField<String> issuingCountry,
        ExtractedField<LocalDate> issueDate,
        ExtractedField<LocalDate> expiryDate,
        ExtractedField<String> passportType) {
}
