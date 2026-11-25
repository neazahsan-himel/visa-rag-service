package com.neaz.travel.visa.domain.model;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Currency;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The official visa fee for one passport/destination/visa-type scope (the same
 * shape as {@link MetadataFilter}), broken into its {@link FeeComponent}s.
 *
 * <p>A fee only exists together with its provenance: the official
 * {@code sourceUrl}, the verbatim {@code sourceQuote} the figures were taken
 * from, and the date a human last verified them. Whether that verification is
 * still recent enough to trust is decided by {@code FeeFreshnessPolicy}, not
 * here. A {@code visaTypeCode} of {@code GENERAL} means the fee applies to
 * every visa type of the destination.
 *
 * <p>No currency conversion is performed: {@link #totalsByCurrency()} totals
 * each currency separately.
 *
 * @param id             identity of this fee version
 * @param passportIso    ISO code of the applicant's passport (e.g. "BD")
 * @param destinationIso ISO code of the visa-issuing destination (e.g. "IN")
 * @param visaTypeCode   visa type code, or "GENERAL" for all visa types
 * @param components     the fee's parts; non-empty, at most one per {@link FeeComponent.Type}
 * @param sourceUrl      official absolute http(s) URL the fee was taken from
 * @param sourceQuote    verbatim text from the source stating the fee
 * @param verifiedAt     date the fee was last verified against the source
 */
public record VisaFee(UUID id,
                      String passportIso,
                      String destinationIso,
                      String visaTypeCode,
                      List<FeeComponent> components,
                      String sourceUrl,
                      String sourceQuote,
                      LocalDate verifiedAt) {

    public VisaFee {
        Objects.requireNonNull(id, "id must not be null");
        requireNonBlank(passportIso, "passportIso");
        requireNonBlank(destinationIso, "destinationIso");
        requireNonBlank(visaTypeCode, "visaTypeCode");
        components = requireValidComponents(components);
        requireHttpUrl(sourceUrl);
        requireNonBlank(sourceQuote, "sourceQuote");
        Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
    }

    /**
     * Totals the components per currency, in the order each currency first
     * appears. Amounts in different currencies are never combined.
     *
     * @return an unmodifiable map from currency to total amount
     */
    public Map<Currency, BigDecimal> totalsByCurrency() {
        Map<Currency, BigDecimal> totals = new LinkedHashMap<>();
        for (FeeComponent component : components) {
            totals.merge(component.currency(), component.amount(), BigDecimal::add);
        }
        return Collections.unmodifiableMap(totals);
    }

    private static List<FeeComponent> requireValidComponents(List<FeeComponent> components) {
        Objects.requireNonNull(components, "components must not be null");
        if (components.isEmpty()) {
            throw new IllegalArgumentException("components must not be empty");
        }
        List<FeeComponent> copy = List.copyOf(components);
        Set<FeeComponent.Type> seen = EnumSet.noneOf(FeeComponent.Type.class);
        for (FeeComponent component : copy) {
            if (!seen.add(component.type())) {
                throw new IllegalArgumentException("duplicate fee component type: " + component.type());
            }
        }
        return copy;
    }

    private static void requireHttpUrl(String sourceUrl) {
        requireNonBlank(sourceUrl, "sourceUrl");
        try {
            URI uri = new URI(sourceUrl);
            String scheme = uri.getScheme();
            if (!uri.isAbsolute() || uri.getHost() == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("sourceUrl must be an absolute http(s) URL: " + sourceUrl);
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("sourceUrl is not a valid URL: " + sourceUrl, e);
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
