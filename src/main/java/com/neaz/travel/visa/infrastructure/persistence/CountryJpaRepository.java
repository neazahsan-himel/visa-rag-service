package com.neaz.travel.visa.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link CountryJpaEntity}. Adds the single
 * lookup {@code DocumentRepositoryAdapter} needs to resolve an ISO country
 * code into the {@code countries.id} FK.
 */
public interface CountryJpaRepository extends JpaRepository<CountryJpaEntity, Long> {

    Optional<CountryJpaEntity> findByIsoCode(String isoCode);
}