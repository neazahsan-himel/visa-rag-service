package com.neaz.travel.visa.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data repository for {@link ApplicationJpaEntity}. {@code findById}
 * and {@code save} are inherited from {@link JpaRepository} and are all
 * {@code ApplicationRepositoryAdapter} needs.
 */
public interface ApplicationJpaRepository extends JpaRepository<ApplicationJpaEntity, UUID> {
}
