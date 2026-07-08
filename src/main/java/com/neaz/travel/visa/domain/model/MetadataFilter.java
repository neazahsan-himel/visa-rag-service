package com.neaz.travel.visa.domain.model;

import java.util.Objects;

/**
 * Scoping filter applied to both the vector search and full-text search
 * retrieval paths before any similarity or rank scoring occurs. An
 * unfiltered retrieval call is a defect in this system.
 *
 * @param passportIso    ISO country code of the applicant's passport; required
 * @param destinationIso ISO country code of the visa-issuing destination; required
 * @param visaTypeCode   visa type code to narrow the search; {@code null} means all visa types
 */
public record MetadataFilter(String passportIso, String destinationIso, String visaTypeCode) {

    public MetadataFilter {
        Objects.requireNonNull(passportIso, "passportIso must not be null");
        Objects.requireNonNull(destinationIso, "destinationIso must not be null");
    }
}