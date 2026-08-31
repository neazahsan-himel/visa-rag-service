package com.neaz.travel.visa.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for {@link ExtractedFieldJpaEntity}.
 * {@code ApplicantProfileRepositoryAdapter} replaces all of a profile's
 * field rows on every save rather than diffing individual changes, which
 * {@code deleteByApplicantProfileId} supports.
 */
public interface ExtractedFieldJpaRepository extends JpaRepository<ExtractedFieldJpaEntity, UUID> {

    List<ExtractedFieldJpaEntity> findByApplicantProfileId(UUID applicantProfileId);

    void deleteByApplicantProfileId(UUID applicantProfileId);
}
