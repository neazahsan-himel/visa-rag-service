package com.neaz.travel.visa.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA mapping for the {@code visa_types} reference table. Persistence-only:
 * there is no corresponding domain model. {@code countryId} is a plain FK
 * column rather than a {@code @ManyToOne} reference, since lookups only
 * need the raw identifier, never the parent {@link CountryJpaEntity} graph.
 */
@Entity
@Table(name = "visa_types")
public class VisaTypeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "country_id", nullable = false)
    private Long countryId;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    protected VisaTypeJpaEntity() {
        // required by JPA
    }

    public VisaTypeJpaEntity(Long countryId, String code, String name) {
        this.countryId = countryId;
        this.code = code;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public Long getCountryId() {
        return countryId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}