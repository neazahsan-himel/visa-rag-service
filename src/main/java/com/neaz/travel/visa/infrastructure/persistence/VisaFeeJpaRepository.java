package com.neaz.travel.visa.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link VisaFeeJpaEntity}. The lookup takes the
 * resolved {@code countries.id} and {@code visa_types.id} FKs, mirroring the
 * {@code idx_visa_fees_active} partial unique index on
 * {@code (country_id, passport_iso, visa_type_id) WHERE active}, which
 * guarantees at most one result.
 */
public interface VisaFeeJpaRepository extends JpaRepository<VisaFeeJpaEntity, UUID> {

    Optional<VisaFeeJpaEntity> findByCountryIdAndPassportIsoAndVisaTypeIdAndActiveTrue(
            Long countryId, String passportIso, Long visaTypeId);
}
