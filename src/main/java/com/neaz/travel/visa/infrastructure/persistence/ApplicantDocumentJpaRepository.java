package com.neaz.travel.visa.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data repository for {@link ApplicantDocumentJpaEntity}. {@code
 * findById} and {@code save} are inherited from {@link JpaRepository} and
 * are all {@code ApplicantDocumentRepositoryAdapter} needs.
 */
public interface ApplicantDocumentJpaRepository extends JpaRepository<ApplicantDocumentJpaEntity, UUID> {
}
