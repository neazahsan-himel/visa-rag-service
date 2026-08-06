package com.neaz.travel.visa.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data repository for {@link QueryAuditJpaEntity}. No derived
 * queries — {@code VisaQueryLogRepository.log(...)} is write-only, so the
 * inherited {@code save} is all {@code VisaQueryLogRepositoryAdapter} needs.
 */
public interface QueryAuditJpaRepository extends JpaRepository<QueryAuditJpaEntity, UUID> {
}