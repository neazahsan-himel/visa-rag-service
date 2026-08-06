package com.neaz.travel.visa.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA mapping for the {@code countries} reference table. Persistence-only:
 * there is no corresponding domain model, since no layer above
 * {@code infrastructure} currently needs a {@code Country} concept.
 */
@Entity
@Table(name = "countries")
public class CountryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "iso_code", nullable = false, unique = true, length = 2)
    private String isoCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected CountryJpaEntity() {
        // required by JPA
    }

    public CountryJpaEntity(String isoCode, String name) {
        this.isoCode = isoCode;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getIsoCode() {
        return isoCode;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}