package com.neaz.travel.visa.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for the {@code extracted_fields} table — one row per {@code
 * ApplicantProfile} field, the relational source of truth for every field
 * path except {@code purposeOfVisit} (see {@link ApplicantProfileJpaEntity}).
 * {@code extractedValue}/{@code normalizedValue} are stored as plain text;
 * converting to/from the field's declared Java type happens in {@code
 * ApplicantProfileRepositoryAdapter}, not here. {@code sourceDocumentId}
 * has no FK constraint yet — {@code applicant_documents} doesn't exist
 * until Phase C. No {@code verifiedAt} column — {@code ExtractedField}
 * doesn't model one, and nothing in Phase B reads it.
 */
@Entity
@Table(name = "extracted_fields")
public class ExtractedFieldJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "applicant_profile_id", nullable = false)
    private UUID applicantProfileId;

    @Column(name = "source_document_id")
    private UUID sourceDocumentId;

    @Column(name = "field_path", nullable = false, length = 200)
    private String fieldPath;

    @Column(name = "extracted_value", nullable = false, columnDefinition = "TEXT")
    private String extractedValue;

    @Column(name = "normalized_value", nullable = false, columnDefinition = "TEXT")
    private String normalizedValue;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "verification_status", nullable = false, length = 20)
    private String verificationStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExtractedFieldJpaEntity() {
        // required by JPA
    }

    public ExtractedFieldJpaEntity(UUID id, UUID applicantProfileId, UUID sourceDocumentId, String fieldPath,
                                    String extractedValue, String normalizedValue, double confidence,
                                    String verificationStatus, Instant createdAt) {
        this.id = id;
        this.applicantProfileId = applicantProfileId;
        this.sourceDocumentId = sourceDocumentId;
        this.fieldPath = fieldPath;
        this.extractedValue = extractedValue;
        this.normalizedValue = normalizedValue;
        this.confidence = confidence;
        this.verificationStatus = verificationStatus;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicantProfileId() {
        return applicantProfileId;
    }

    public UUID getSourceDocumentId() {
        return sourceDocumentId;
    }

    public String getFieldPath() {
        return fieldPath;
    }

    public String getExtractedValue() {
        return extractedValue;
    }

    public String getNormalizedValue() {
        return normalizedValue;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
