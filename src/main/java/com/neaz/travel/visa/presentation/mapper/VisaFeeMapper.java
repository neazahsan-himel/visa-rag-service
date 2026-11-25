package com.neaz.travel.visa.presentation.mapper;

import com.neaz.travel.visa.application.result.VisaFeeResult;
import com.neaz.travel.visa.domain.model.FeeComponent;
import com.neaz.travel.visa.presentation.dto.VisaFeeResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates the application layer's {@link VisaFeeResult} into the
 * {@link VisaFeeResponse} wire shape. Currency totals keep the result's
 * iteration order (the order each currency first appears in the fee), so the
 * JSON is stable once a fee has more than one currency.
 */
@Component
public class VisaFeeMapper {

    public VisaFeeResponse toResponse(VisaFeeResult result) {
        List<VisaFeeResponse.FeeComponentDto> components = result.components().stream()
                .map(this::toFeeComponentDto)
                .toList();
        return new VisaFeeResponse(
                result.passportIso(),
                result.destinationIso(),
                result.requestedVisaTypeCode(),
                result.appliedVisaTypeCode(),
                components,
                toTotalsByCurrencyCode(result.totalsByCurrency()),
                result.sourceUrl(),
                result.sourceQuote(),
                result.verifiedAt(),
                result.stale(),
                result.staleFrom());
    }

    private VisaFeeResponse.FeeComponentDto toFeeComponentDto(FeeComponent component) {
        return new VisaFeeResponse.FeeComponentDto(
                component.type().name(),
                component.chargedBy(),
                component.amount(),
                component.currency().getCurrencyCode());
    }

    private Map<String, BigDecimal> toTotalsByCurrencyCode(Map<Currency, BigDecimal> totals) {
        Map<String, BigDecimal> byCode = new LinkedHashMap<>();
        totals.forEach((currency, amount) -> byCode.put(currency.getCurrencyCode(), amount));
        return byCode;
    }
}
