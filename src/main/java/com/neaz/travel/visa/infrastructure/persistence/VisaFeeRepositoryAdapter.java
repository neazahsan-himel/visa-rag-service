package com.neaz.travel.visa.infrastructure.persistence;

import com.neaz.travel.visa.domain.model.FeeComponent;
import com.neaz.travel.visa.domain.model.VisaFee;
import com.neaz.travel.visa.domain.repository.VisaFeeRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

/**
 * {@link VisaFeeRepository} implementation backed by PostgreSQL via Spring
 * Data JPA. Resolves the destination ISO code and visa type code into their
 * FK ids, then loads the active fee and its components in one read-only
 * transaction.
 *
 * <p>An unknown destination or visa type yields {@link Optional#empty()},
 * the same as a known scope with no fee. A stored component type or currency
 * code that cannot be converted is a data error and fails loudly rather than
 * being skipped, since silently dropping a component would misstate the fee.
 */
@Repository
public class VisaFeeRepositoryAdapter implements VisaFeeRepository {

    private static final Comparator<FeeComponent> POLICY_ORDER = Comparator.comparing(FeeComponent::type);

    private final VisaFeeJpaRepository visaFeeJpaRepository;
    private final VisaFeeComponentJpaRepository visaFeeComponentJpaRepository;
    private final CountryJpaRepository countryJpaRepository;
    private final VisaTypeJpaRepository visaTypeJpaRepository;

    public VisaFeeRepositoryAdapter(VisaFeeJpaRepository visaFeeJpaRepository,
                                    VisaFeeComponentJpaRepository visaFeeComponentJpaRepository,
                                    CountryJpaRepository countryJpaRepository,
                                    VisaTypeJpaRepository visaTypeJpaRepository) {
        this.visaFeeJpaRepository = visaFeeJpaRepository;
        this.visaFeeComponentJpaRepository = visaFeeComponentJpaRepository;
        this.countryJpaRepository = countryJpaRepository;
        this.visaTypeJpaRepository = visaTypeJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VisaFee> findActive(String passportIso, String destinationIso, String visaTypeCode) {
        return countryJpaRepository.findByIsoCode(destinationIso)
                .flatMap(country -> visaTypeJpaRepository.findByCountryIdAndCode(country.getId(), visaTypeCode))
                .flatMap(visaType -> visaFeeJpaRepository.findByCountryIdAndPassportIsoAndVisaTypeIdAndActiveTrue(
                        visaType.getCountryId(), passportIso, visaType.getId()))
                .map(entity -> toDomain(entity, destinationIso, visaTypeCode));
    }

    private VisaFee toDomain(VisaFeeJpaEntity entity, String destinationIso, String visaTypeCode) {
        List<FeeComponent> components = visaFeeComponentJpaRepository.findByVisaFeeId(entity.getId()).stream()
                .map(this::toDomain)
                .sorted(POLICY_ORDER)
                .toList();
        return new VisaFee(
                entity.getId(),
                entity.getPassportIso(),
                destinationIso,
                visaTypeCode,
                components,
                entity.getSourceUrl(),
                entity.getSourceQuote(),
                entity.getVerifiedAt());
    }

    private FeeComponent toDomain(VisaFeeComponentJpaEntity entity) {
        return new FeeComponent(
                FeeComponent.Type.valueOf(entity.getComponentType()),
                entity.getChargedBy(),
                entity.getAmount(),
                Currency.getInstance(entity.getCurrency()));
    }
}
