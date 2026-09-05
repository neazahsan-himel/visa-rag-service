package com.neaz.travel.visa.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for the {@code applicant_documents} table. {@code documentType}
 * and {@code status} are stored as plain text, converted to/from their
 * domain enums in {@code ApplicantDocumentRepositoryAdapter} — same pattern
 * as {@code verificationStatus} on {@link ExtractedFieldJpaEntity}.
 */
@Entity
@Table(name = "applicant_documents")
public class ApplicantDocumentJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    @Column(name = "document_type", nullable = false, length = 30)
    private String documentType;

    @Column(name = "storage_path", nullable = false, columnDefinition = "TEXT")
    private String storagePath;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    protected ApplicantDocumentJpaEntity() {
        // required by JPA
    }

    public ApplicantDocumentJpaEntity(UUID id, UUID applicantId, String documentType, String storagePath,
                                       Instant uploadedAt, String status) {
        this.id = id;
        this.applicantId = applicantId;
        this.documentType = documentType;
        this.storagePath = storagePath;
        this.uploadedAt = uploadedAt;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicantId() {
        return applicantId;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public String getStatus() {
        return status;
    }
}
