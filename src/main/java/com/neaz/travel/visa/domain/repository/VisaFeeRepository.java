package com.neaz.travel.visa.domain.repository;

import com.neaz.travel.visa.domain.model.VisaFee;

import java.util.Optional;

/**
 * Read-only repository contract for {@link VisaFee}s, implemented by
 * {@code infrastructure/persistence} against PostgreSQL. Fees are currently
 * maintained through Flyway migrations, so no write operation is exposed.
 *
 * <p>Lookups are exact: this repository does not fall back from a specific
 * visa type to {@code GENERAL}. Which fee applies is a business decision made
 * by the application layer.
 */
public interface VisaFeeRepository {

    /**
     * Finds the currently active fee for exactly this passport, destination
     * and visa type.
     *
     * @param passportIso    ISO code of the applicant's passport (e.g. "BD")
     * @param destinationIso ISO code of the visa-issuing destination (e.g. "IN")
     * @param visaTypeCode   exact visa type code, including "GENERAL"
     * @return the active fee for that scope, if one exists
     */
    Optional<VisaFee> findActive(String passportIso, String destinationIso, String visaTypeCode);
}
