package com.neaz.travel.visa.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

import com.neaz.travel.visa.domain.model.SourceCheckLog;

/**
 * JPA mapping for the {@code source_check_log} table. {@code id} is
 * DB-generated, same rationale as {@link QueryAuditJpaEntity} — the write
 * path never needs it back. {@code reviewed} is the one mutable field,
 * flipped by the pending-changes review workflow after the row is written.
 */
@Entity
@Table(name = "source_check_log")
public class SourceCheckLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "checked_at", nullable = false, updatable = false, insertable = false)
    private Instant checkedAt;

    @Column(name = "detected_hash", length = 64)
    private String detectedHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SourceCheckLog.Status status;

    @Column(name = "reviewed", nullable = false)
    private boolean reviewed;

    protected SourceCheckLogJpaEntity() {
        // required by JPA
    }

    public SourceCheckLogJpaEntity(UUID documentId, String detectedHash, SourceCheckLog.Status status) {
        this.documentId = documentId;
        this.detectedHash = detectedHash;
        this.status = status;
        this.reviewed = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public String getDetectedHash() {
        return detectedHash;
    }

    public SourceCheckLog.Status getStatus() {
        return status;
    }

    public boolean isReviewed() {
        return reviewed;
    }

    public void setReviewed(boolean reviewed) {
        this.reviewed = reviewed;
    }
}
