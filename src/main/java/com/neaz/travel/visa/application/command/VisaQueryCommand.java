package com.neaz.travel.visa.application.command;

import com.neaz.travel.visa.domain.model.MetadataFilter;

import java.util.Objects;

/**
 * Input to the visa query use case, built by {@code VisaQueryController} from
 * a {@code POST /api/v1/visa/query} request.
 *
 * @param queryText the natural-language question text
 * @param filter    the passport/destination/visa-type scope for this query
 */
public record VisaQueryCommand(String queryText, MetadataFilter filter) {

    public VisaQueryCommand {
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("queryText must not be blank");
        }
        Objects.requireNonNull(filter, "filter must not be null");
    }
}