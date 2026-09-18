package com.neaz.travel.visa.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for the {@code applications} table. {@code status} is stored
 * as plain text, converted to/from the domain enum in {@code
 * ApplicationRepositoryAdapter} — same pattern as {@code
 * ApplicantDocumentJpaEntity.status}.
 */
@Entity
@Table(name = "applications")
public class ApplicationJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    @Column(name = "passport_iso", nullable = false, length = 2)
    private String passportIso;

    @Column(name = "destination_iso", nullable = false, length = 2)
    private String destinationIso;

    @Column(name = "visa_type_code", nullable = false, length = 30)
    private String visaTypeCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    protected ApplicationJpaEntity() {
        // required by JPA
    }

    public ApplicationJpaEntity(UUID id, UUID applicantId, String passportIso, String destinationIso,
                                 String visaTypeCode, Instant createdAt, String status) {
        this.id = id;
        this.applicantId = applicantId;
        this.passportIso = passportIso;
        this.destinationIso = destinationIso;
        this.visaTypeCode = visaTypeCode;
        this.createdAt = createdAt;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicantId() {
        return applicantId;
    }

    public String getPassportIso() {
        return passportIso;
    }

    public String getDestinationIso() {
        return destinationIso;
    }

    public String getVisaTypeCode() {
        return visaTypeCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getStatus() {
        return status;
    }
}
