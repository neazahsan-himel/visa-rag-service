package com.neaz.travel.visa.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA mapping for the {@code visa_fees} table. {@code countryId} and
 * {@code visaTypeId} are plain FK columns resolved by the adapter, not JPA
 * relationships, matching {@link DocumentJpaEntity}. The fee's components live
 * in {@link VisaFeeComponentJpaEntity} and are loaded separately by
 * {@code visaFeeId}.
 *
 * <p>Fees are maintained through Flyway migrations, so this entity exposes no
 * setters.
 */
@Entity
@Table(name = "visa_fees")
public class VisaFeeJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "country_id", nullable = false)
    private Long countryId;

    @Column(name = "visa_type_id", nullable = false)
    private Long visaTypeId;

    @Column(name = "passport_iso", nullable = false, length = 2)
    private String passportIso;

    @Column(name = "source_url", nullable = false)
    private String sourceUrl;

    @Column(name = "source_quote", nullable = false)
    private String sourceQuote;

    @Column(name = "verified_at", nullable = false)
    private LocalDate verifiedAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VisaFeeJpaEntity() {
        // required by JPA
    }

    public VisaFeeJpaEntity(UUID id, Long countryId, Long visaTypeId, String passportIso, String sourceUrl,
                            String sourceQuote, LocalDate verifiedAt, boolean active, Instant createdAt) {
        this.id = id;
        this.countryId = countryId;
        this.visaTypeId = visaTypeId;
        this.passportIso = passportIso;
        this.sourceUrl = sourceUrl;
        this.sourceQuote = sourceQuote;
        this.verifiedAt = verifiedAt;
        this.active = active;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public Long getCountryId() {
        return countryId;
    }

    public Long getVisaTypeId() {
        return visaTypeId;
    }

    public String getPassportIso() {
        return passportIso;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getSourceQuote() {
        return sourceQuote;
    }

    public LocalDate getVerifiedAt() {
        return verifiedAt;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
