package com.neaz.travel.visa.application.result;

import com.neaz.travel.visa.domain.model.FeeComponent;
import com.neaz.travel.visa.domain.model.VisaFee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Outcome of a visa fee lookup, returned by {@code VisaFeeQueryService}.
 *
 * <p>{@code appliedVisaTypeCode} is the visa type of the fee actually used and
 * may differ from {@code requestedVisaTypeCode}: when no fee exists for the
 * requested type, the destination's {@code GENERAL} fee applies. Exposing
 * both keeps that fallback visible, so a flat fee is never presented as a
 * type-specific one.
 *
 * @param passportIso           ISO code of the applicant's passport
 * @param destinationIso        ISO code of the visa-issuing destination
 * @param requestedVisaTypeCode visa type the caller asked about
 * @param appliedVisaTypeCode   visa type of the fee returned (the requested type or "GENERAL")
 * @param components            fee components in official policy order
 * @param totalsByCurrency      total per currency; currencies are never combined
 * @param sourceUrl             official URL the fee was taken from
 * @param sourceQuote           verbatim source text stating the fee
 * @param verifiedAt            date the fee was last verified against the source
 * @param stale                 whether the verification is older than the freshness policy allows
 * @param staleFrom             first date on which the fee is considered stale
 */
public record VisaFeeResult(
        String passportIso,
        String destinationIso,
        String requestedVisaTypeCode,
        String appliedVisaTypeCode,
        List<FeeComponent> components,
        Map<Currency, BigDecimal> totalsByCurrency,
        String sourceUrl,
        String sourceQuote,
        LocalDate verifiedAt,
        boolean stale,
        LocalDate staleFrom) {

    public VisaFeeResult {
        Objects.requireNonNull(requestedVisaTypeCode, "requestedVisaTypeCode must not be null");
        Objects.requireNonNull(appliedVisaTypeCode, "appliedVisaTypeCode must not be null");
        components = List.copyOf(Objects.requireNonNull(components, "components must not be null"));
        totalsByCurrency = Objects.requireNonNull(totalsByCurrency, "totalsByCurrency must not be null");
        Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
        Objects.requireNonNull(staleFrom, "staleFrom must not be null");
    }

    /**
     * Maps a domain {@link VisaFee} and its freshness verdict to this
     * read-only result shape.
     *
     * @param fee                   the fee that applies
     * @param requestedVisaTypeCode the visa type the caller asked about
     * @param stale                 the freshness verdict for {@code fee}
     * @param staleFrom             the first stale date for {@code fee}
     */
    public static VisaFeeResult from(VisaFee fee, String requestedVisaTypeCode, boolean stale, LocalDate staleFrom) {
        return new VisaFeeResult(fee.passportIso(), fee.destinationIso(), requestedVisaTypeCode, fee.visaTypeCode(),
                fee.components(), fee.totalsByCurrency(), fee.sourceUrl(), fee.sourceQuote(), fee.verifiedAt(),
                stale, staleFrom);
    }
}
