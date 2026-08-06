package com.neaz.travel.visa.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link VisaTypeJpaEntity}. The lookup is
 * scoped by {@code countryId} as well as {@code code}, matching the table's
 * {@code UNIQUE (country_id, code)} constraint — the same code could exist
 * under a different country once more destinations are supported.
 */
public interface VisaTypeJpaRepository extends JpaRepository<VisaTypeJpaEntity, Long> {

    Optional<VisaTypeJpaEntity> findByCountryIdAndCode(Long countryId, String code);
}