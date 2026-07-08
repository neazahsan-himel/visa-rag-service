package com.neaz.travel.visa.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Descriptive metadata attached to an ingested {@link Document}: which
 * destination and passport it applies to, which visa type it covers, and
 * where it came from.
 *
 * @param destinationIso ISO country code of the visa-issuing destination (e.g. "IN")
 * @param passportIso    ISO country code of the applicant's passport (e.g. "BD")
 * @param visaTypeCode   visa type code (e.g. "TOURIST", "BUSINESS")
 * @param sourceUrl      official source URL the document was retrieved from; may be {@code null}
 * @param lastUpdated    the date the source document was last updated
 * @param version        document version label; may be {@code null}
 */
public record DocumentMetadata(
        String destinationIso,
        String passportIso,
        String visaTypeCode,
        String sourceUrl,
        LocalDate lastUpdated,
        String version) {

    public DocumentMetadata {
        Objects.requireNonNull(destinationIso, "destinationIso must not be null");
        Objects.requireNonNull(passportIso, "passportIso must not be null");
        Objects.requireNonNull(visaTypeCode, "visaTypeCode must not be null");
        Objects.requireNonNull(lastUpdated, "lastUpdated must not be null");
    }
}