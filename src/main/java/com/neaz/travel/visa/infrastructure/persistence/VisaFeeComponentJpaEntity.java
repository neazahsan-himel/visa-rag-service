package com.neaz.travel.visa.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * JPA mapping for the {@code visa_fee_components} table. {@code visaFeeId} is
 * a plain FK column to {@link VisaFeeJpaEntity}, not a JPA relationship.
 * {@code componentType} and {@code currency} are stored as their string codes
 * (a {@code FeeComponent.Type} name and an ISO 4217 code) and converted by the
 * adapter, keeping domain types out of the persistence mapping.
 *
 * <p>Fees are maintained through Flyway migrations, so this entity exposes no
 * setters.
 */
@Entity
@Table(name = "visa_fee_components")
public class VisaFeeComponentJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "visa_fee_id", nullable = false)
    private UUID visaFeeId;

    @Column(name = "component_type", nullable = false, length = 20)
    private String componentType;

    @Column(name = "charged_by", nullable = false, length = 200)
    private String chargedBy;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    protected VisaFeeComponentJpaEntity() {
        // required by JPA
    }

    public VisaFeeComponentJpaEntity(UUID id, UUID visaFeeId, String componentType, String chargedBy,
                                     BigDecimal amount, String currency) {
        this.id = id;
        this.visaFeeId = visaFeeId;
        this.componentType = componentType;
        this.chargedBy = chargedBy;
        this.amount = amount;
        this.currency = currency;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVisaFeeId() {
        return visaFeeId;
    }

    public String getComponentType() {
        return componentType;
    }

    public String getChargedBy() {
        return chargedBy;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }
}
