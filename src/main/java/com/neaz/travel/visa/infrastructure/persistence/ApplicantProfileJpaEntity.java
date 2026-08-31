package com.neaz.travel.visa.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for the {@code applicant_profiles} table. Carries only
 * identity, timestamps, and {@code purposeOfVisit} — every other profile
 * field lives in {@code extracted_fields} (see {@link ExtractedFieldJpaEntity}),
 * per the relational-per-field storage decision. {@code id} is app-supplied,
 * same rationale as {@link DocumentJpaEntity}.
 */
@Entity
@Table(name = "applicant_profiles")
public class ApplicantProfileJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    @Column(name = "purpose_of_visit")
    private String purposeOfVisit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;

    protected ApplicantProfileJpaEntity() {
        // required by JPA
    }

    public ApplicantProfileJpaEntity(UUID id, UUID applicantId, String purposeOfVisit,
                                      Instant createdAt, Instant lastModifiedAt) {
        this.id = id;
        this.applicantId = applicantId;
        this.purposeOfVisit = purposeOfVisit;
        this.createdAt = createdAt;
        this.lastModifiedAt = lastModifiedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicantId() {
        return applicantId;
    }

    public String getPurposeOfVisit() {
        return purposeOfVisit;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastModifiedAt() {
        return lastModifiedAt;
    }
}
