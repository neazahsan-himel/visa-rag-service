package com.neaz.travel.visa.presentation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Response body for {@code GET /api/v1/visa/fees}, wrapped in
 * {@code ApiResponse<VisaFeeResponse>}. Mirrors {@code VisaFeeResult}'s shape
 * without referencing any domain type directly.
 *
 * @param passportIso           ISO code of the applicant's passport
 * @param destinationIso        ISO code of the visa-issuing destination
 * @param requestedVisaTypeCode visa type the caller asked about
 * @param appliedVisaTypeCode   visa type of the fee returned; "GENERAL" when no type-specific fee exists
 * @param components            fee components in official policy order
 * @param totalsByCurrency      total per ISO 4217 currency code; currencies are never combined
 * @param sourceUrl             official URL the fee was taken from
 * @param sourceQuote           verbatim source text stating the fee
 * @param verifiedAt            date the fee was last verified against the source
 * @param stale                 whether the verification is older than the freshness policy allows
 * @param staleFrom             first date on which the fee is considered stale
 */
public record VisaFeeResponse(
        String passportIso,
        String destinationIso,
        String requestedVisaTypeCode,
        String appliedVisaTypeCode,
        List<FeeComponentDto> components,
        Map<String, BigDecimal> totalsByCurrency,
        String sourceUrl,
        String sourceQuote,
        LocalDate verifiedAt,
        boolean stale,
        LocalDate staleFrom) {

    /**
     * Wire shape of {@code FeeComponent}.
     *
     * @param type      "BASIC", "SPECIAL" or "PROCESSING"
     * @param chargedBy who collects this component
     * @param amount    the amount, never negative
     * @param currency  ISO 4217 currency code (e.g. "BDT")
     */
    public record FeeComponentDto(String type, String chargedBy, BigDecimal amount, String currency) {
    }
}
