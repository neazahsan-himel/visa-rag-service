package com.neaz.travel.visa.domain.model;

import java.util.Objects;

/**
 * A parsed user query, scoped to a specific passport/destination/visa-type
 * context via {@link MetadataFilter}.
 *
 * @param queryText the natural-language question text
 * @param filter    the passport/destination/visa-type scope for this query
 */
public record VisaQuery(String queryText, MetadataFilter filter) {

    public VisaQuery {
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("queryText must not be blank");
        }
        Objects.requireNonNull(filter, "filter must not be null");
    }
}