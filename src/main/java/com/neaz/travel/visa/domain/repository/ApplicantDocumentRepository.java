package com.neaz.travel.visa.domain.repository;

import com.neaz.travel.visa.domain.model.ApplicantDocument;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository contract for {@link ApplicantDocument} entities, implemented
 * by {@code infrastructure/persistence} against PostgreSQL.
 */
public interface ApplicantDocumentRepository {

    ApplicantDocument save(ApplicantDocument document);

    Optional<ApplicantDocument> findById(UUID id);
}
