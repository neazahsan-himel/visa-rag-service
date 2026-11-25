package com.neaz.travel.visa.application.query;

import com.neaz.travel.visa.application.result.VisaFeeResult;
import com.neaz.travel.visa.domain.model.VisaFee;
import com.neaz.travel.visa.domain.repository.VisaFeeRepository;
import com.neaz.travel.visa.domain.service.FeeFreshnessPolicy;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

/**
 * Looks up the official fee for a passport/destination/visa-type scope and
 * attaches its freshness verdict.
 *
 * <p>A fee recorded for the exact visa type takes precedence; otherwise the
 * destination's {@code GENERAL} fee applies, and the result's
 * {@code appliedVisaTypeCode} says so. If neither exists the result is empty —
 * a fee is never inferred or estimated.
 */
@Service
public class VisaFeeQueryService {

    static final String GENERAL_VISA_TYPE_CODE = "GENERAL";

    private final VisaFeeRepository visaFeeRepository;
    private final FeeFreshnessPolicy feeFreshnessPolicy;

    public VisaFeeQueryService(VisaFeeRepository visaFeeRepository, Clock clock) {
        this.visaFeeRepository = visaFeeRepository;
        this.feeFreshnessPolicy = new FeeFreshnessPolicy(clock);
    }

    /**
     * @param passportIso    ISO code of the applicant's passport (e.g. "BD")
     * @param destinationIso ISO code of the visa-issuing destination (e.g. "IN")
     * @param visaTypeCode   visa type to look up (e.g. "TOURIST"), or "GENERAL"
     * @return the applicable fee with its freshness verdict, or empty if no official fee is recorded
     */
    public Optional<VisaFeeResult> findFee(String passportIso, String destinationIso, String visaTypeCode) {
        return visaFeeRepository.findActive(passportIso, destinationIso, visaTypeCode)
                .or(() -> findGeneralFallback(passportIso, destinationIso, visaTypeCode))
                .map(fee -> toResult(fee, visaTypeCode));
    }

    private Optional<VisaFee> findGeneralFallback(String passportIso, String destinationIso, String visaTypeCode) {
        if (GENERAL_VISA_TYPE_CODE.equals(visaTypeCode)) {
            return Optional.empty();
        }
        return visaFeeRepository.findActive(passportIso, destinationIso, GENERAL_VISA_TYPE_CODE);
    }

    private VisaFeeResult toResult(VisaFee fee, String requestedVisaTypeCode) {
        return VisaFeeResult.from(fee, requestedVisaTypeCode,
                feeFreshnessPolicy.isStale(fee), feeFreshnessPolicy.staleFrom(fee));
    }
}
