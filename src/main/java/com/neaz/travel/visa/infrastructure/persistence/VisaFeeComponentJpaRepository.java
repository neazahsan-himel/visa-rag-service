package com.neaz.travel.visa.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for {@link VisaFeeComponentJpaEntity}. Results are
 * unordered: the adapter orders components by {@code FeeComponent.Type}
 * declaration order, which follows the official fee policy rather than the
 * alphabetical order a SQL {@code ORDER BY} on the stored code would give.
 * Served by the {@code UNIQUE (visa_fee_id, component_type)} index.
 */
public interface VisaFeeComponentJpaRepository extends JpaRepository<VisaFeeComponentJpaEntity, UUID> {

    List<VisaFeeComponentJpaEntity> findByVisaFeeId(UUID visaFeeId);
}
