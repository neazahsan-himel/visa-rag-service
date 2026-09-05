package com.neaz.travel.visa.infrastructure.persistence;

import com.neaz.travel.visa.domain.model.ApplicantDocument;
import com.neaz.travel.visa.domain.model.DocumentProcessingStatus;
import com.neaz.travel.visa.domain.model.DocumentType;
import com.neaz.travel.visa.domain.repository.ApplicantDocumentRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link ApplicantDocumentRepository} implementation backed by PostgreSQL
 * via Spring Data JPA.
 */
@Repository
public class ApplicantDocumentRepositoryAdapter implements ApplicantDocumentRepository {

    private final ApplicantDocumentJpaRepository applicantDocumentJpaRepository;

    public ApplicantDocumentRepositoryAdapter(ApplicantDocumentJpaRepository applicantDocumentJpaRepository) {
        this.applicantDocumentJpaRepository = applicantDocumentJpaRepository;
    }

    @Override
    public ApplicantDocument save(ApplicantDocument document) {
        applicantDocumentJpaRepository.save(toEntity(document));
        return document;
    }

    @Override
    public Optional<ApplicantDocument> findById(UUID id) {
        return applicantDocumentJpaRepository.findById(id).map(this::toDomain);
    }

    private ApplicantDocumentJpaEntity toEntity(ApplicantDocument document) {
        return new ApplicantDocumentJpaEntity(
                document.getId(),
                document.getApplicantId(),
                document.getDocumentType().name(),
                document.getStoragePath(),
                document.getUploadedAt(),
                document.getStatus().name());
    }

    private ApplicantDocument toDomain(ApplicantDocumentJpaEntity entity) {
        return new ApplicantDocument(
                entity.getId(),
                entity.getApplicantId(),
                DocumentType.valueOf(entity.getDocumentType()),
                entity.getStoragePath(),
                entity.getUploadedAt(),
                DocumentProcessingStatus.valueOf(entity.getStatus()));
    }
}
