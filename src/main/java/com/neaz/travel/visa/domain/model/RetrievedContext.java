package com.neaz.travel.visa.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * The merged, ranked result of hybrid retrieval for a single query, ready to
 * be scored by {@code ConfidenceScorer} and rendered by
 * {@code VisaPromptTemplate}. Carries only plain Java types — no
 * infrastructure-specific representations enter the domain.
 *
 * @param queryText     the original user query text, used for keyword-match scoring
 * @param scoredChunks  the merged, RRF-scored chunks, in ranked order
 */
public record
RetrievedContext(String queryText, List<ScoredChunk> scoredChunks) {

    public RetrievedContext {
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("queryText must not be blank");
        }
        scoredChunks = List.copyOf(Objects.requireNonNull(scoredChunks, "scoredChunks must not be null"));
    }
}