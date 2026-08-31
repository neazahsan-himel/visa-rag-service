package com.neaz.travel.visa.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link ApplicantProfileJpaEntity}. {@code
 * findById} and {@code save} are inherited from {@link JpaRepository}; the
 * applicant-id lookup backs {@code ApplicantProfileRepositoryAdapter}'s
 * find-or-create flow.
 */
public interface ApplicantProfileJpaRepository extends JpaRepository<ApplicantProfileJpaEntity, UUID> {

    Optional<ApplicantProfileJpaEntity> findByApplicantId(UUID applicantId);
}
