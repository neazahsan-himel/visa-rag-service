package com.neaz.travel.visa.domain.repository;

import com.neaz.travel.visa.domain.model.ApplicantProfile;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository contract for {@link ApplicantProfile} aggregates, implemented
 * by {@code infrastructure/persistence} against PostgreSQL.
 */
public interface ApplicantProfileRepository {

    ApplicantProfile save(ApplicantProfile profile);

    Optional<ApplicantProfile> findById(UUID id);

    /** Each applicant has exactly one profile in the current design. */
    Optional<ApplicantProfile> findByApplicantId(UUID applicantId);
}
