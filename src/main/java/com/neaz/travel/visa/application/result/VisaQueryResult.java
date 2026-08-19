package com.neaz.travel.visa.application.result;

import com.neaz.travel.visa.domain.model.Citation;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of the visa query use case, returned by {@code VisaQueryService} to
 * {@code VisaQueryController}.
 *
 * @param answerText      the generated answer text
 * @param citations       citations backing every factual statement in the answer
 * @param confidenceScore score in {@code [0.0, 1.0]} computed by {@code ConfidenceScorer}
 */
public record VisaQueryResult(String answerText, List<Citation> citations, double confidenceScore) {

    public VisaQueryResult {
        if (answerText == null || answerText.isBlank()) {
            throw new IllegalArgumentException("answerText must not be blank");
        }
        citations = List.copyOf(Objects.requireNonNull(citations, "citations must not be null"));
        if (confidenceScore < 0.0 || confidenceScore > 1.0) {
            throw new IllegalArgumentException("confidenceScore must be within [0.0, 1.0]");
        }
    }
}