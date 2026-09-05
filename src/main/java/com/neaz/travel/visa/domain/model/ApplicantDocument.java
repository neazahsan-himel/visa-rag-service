package com.neaz.travel.visa.domain.model;

import com.neaz.travel.visa.common.exception.VisaRagException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An applicant-uploaded document (e.g. a passport scan) moving through the
 * document-intelligence pipeline. Identity is defined by {@code id}.
 *
 * <p>Lifecycle: {@code UPLOADED} to either {@code QUALITY_REJECTED} (image
 * failed the pre-OCR quality gate) or {@code EXTRACTED} (OCR/MRZ candidates
 * are ready for the applicant to review), then {@code CONFIRMED} once the
 * applicant accepts the candidates and they are written into their
 * {@link ApplicantProfile}. Each upload attempt is its own row; a rejected
 * or abandoned upload is not retried in place.
 */
public final class ApplicantDocument {

    private final UUID id;
    private final UUID applicantId;
    private final DocumentType documentType;
    private final String storagePath;
    private final Instant uploadedAt;
    private DocumentProcessingStatus status;

    public ApplicantDocument(UUID id, UUID applicantId, DocumentType documentType, String storagePath,
                              Instant uploadedAt, DocumentProcessingStatus status) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.applicantId = Objects.requireNonNull(applicantId, "applicantId must not be null");
        this.documentType = Objects.requireNonNull(documentType, "documentType must not be null");
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("storagePath must not be blank");
        }
        this.storagePath = storagePath;
        this.uploadedAt = Objects.requireNonNull(uploadedAt, "uploadedAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    /**
     * Marks this document as having failed the pre-OCR image-quality gate.
     * Terminal: the applicant must upload a new document rather than retry this one.
     *
     * @throws VisaRagException if this document is not currently {@code UPLOADED}
     */
    public void rejectForQuality() {
        requireStatus(DocumentProcessingStatus.UPLOADED, "reject for quality");
        this.status = DocumentProcessingStatus.QUALITY_REJECTED;
    }

    /**
     * Marks this document as having produced field candidates the applicant
     * must review before anything is saved.
     *
     * @throws VisaRagException if this document is not currently {@code UPLOADED}
     */
    public void markExtracted() {
        requireStatus(DocumentProcessingStatus.UPLOADED, "mark extracted");
        this.status = DocumentProcessingStatus.EXTRACTED;
    }

    /**
     * Marks this document's extracted candidates as reviewed and persisted
     * into the applicant's profile.
     *
     * @throws VisaRagException if this document is not currently {@code EXTRACTED}
     */
    public void confirm() {
        requireStatus(DocumentProcessingStatus.EXTRACTED, "confirm");
        this.status = DocumentProcessingStatus.CONFIRMED;
    }

    private void requireStatus(DocumentProcessingStatus expected, String action) {
        if (status != expected) {
            throw new VisaRagException(
                    "Cannot " + action + " document '" + id + "': expected status " + expected + " but was " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicantId() {
        return applicantId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public DocumentProcessingStatus getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApplicantDocument other)) {
            return false;
        }
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "ApplicantDocument{id=%s, applicantId=%s, documentType=%s, status=%s}"
                .formatted(id, applicantId, documentType, status);
    }
}
