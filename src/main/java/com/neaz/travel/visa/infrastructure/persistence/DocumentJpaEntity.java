package com.neaz.travel.visa.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA mapping for the {@code documents} table. The {@code id} is app-supplied
 * (never {@code @GeneratedValue}) because the domain {@link
 * com.neaz.travel.visa.domain.model.Document} constructor requires a
 * non-null identifier already assigned by the caller. {@code countryId} and
 * {@code visaTypeId} are plain FK columns resolved by the adapter, not JPA
 * relationships.
 */
@Entity
@Table(name = "documents")
public class DocumentJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "country_id", nullable = false)
    private Long countryId;

    @Column(name = "visa_type_id", nullable = false)
    private Long visaTypeId;

    @Column(name = "passport_iso", nullable = false, length = 2)
    private String passportIso;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "last_updated", nullable = false)
    private LocalDate lastUpdated;

    @Column(name = "version", length = 20)
    private String version;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected DocumentJpaEntity() {
        // required by JPA
    }

    public DocumentJpaEntity(UUID id, Long countryId, Long visaTypeId, String passportIso, String title,
                              String sourceUrl, String fileHash, LocalDate lastUpdated, String version,
                              Instant ingestedAt, boolean active) {
        this.id = id;
        this.countryId = countryId;
        this.visaTypeId = visaTypeId;
        this.passportIso = passportIso;
        this.title = title;
        this.sourceUrl = sourceUrl;
        this.fileHash = fileHash;
        this.lastUpdated = lastUpdated;
        this.version = version;
        this.ingestedAt = ingestedAt;
        this.active = active;
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

    public String getTitle() {
        return title;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getFileHash() {
        return fileHash;
    }

    public LocalDate getLastUpdated() {
        return lastUpdated;
    }

    public String getVersion() {
        return version;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }

    public boolean isActive() {
        return active;
    }
}