package com.neaz.travel.visa.domain.service;

import com.neaz.travel.visa.domain.model.RetrievedContext;
import com.neaz.travel.visa.domain.model.ScoredChunk;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Computes a confidence score in {@code [0.0, 1.0]} for a {@link RetrievedContext},
 * combining four weighted factors: mean fusion score (0.50), retrieved chunk
 * count (0.20), keyword match ratio (0.20), and document recency (0.10).
 * Operates only on plain Java types — no infrastructure objects enter this class.
 * This service only computes the score; comparing it against the confidence
 * threshold and deciding whether to fall back is the caller's responsibility.
 */
public class ConfidenceScorer {

    private static final double FUSION_SCORE_WEIGHT = 0.50;
    private static final double CHUNK_COUNT_WEIGHT = 0.20;
    private static final double KEYWORD_MATCH_WEIGHT = 0.20;
    private static final double RECENCY_WEIGHT = 0.10;

    /** RRF (k=60, two retrieval paths) rarely exceeds this per chunk; used to normalize into [0,1]. */
    private static final double MAX_EXPECTED_FUSION_SCORE = 0.05;

    /** Target number of chunks for a "healthy" retrieved context; more is capped, not rewarded further. */
    private static final int EXPECTED_CHUNK_COUNT = 10;

    private static final int RECENCY_FULL_SCORE_DAYS = 365;
    private static final int RECENCY_ZERO_SCORE_DAYS = 365 * 5;

    private static final Pattern TOKEN_DELIMITER = Pattern.compile("[^a-zA-Z0-9]+");

    public double score(RetrievedContext context) {
        Objects.requireNonNull(context, "context must not be null");
        List<ScoredChunk> scoredChunks = context.scoredChunks();
        if (scoredChunks.isEmpty()) {
            return 0.0;
        }

        double total = FUSION_SCORE_WEIGHT * normalizedMeanFusionScore(scoredChunks)
                + CHUNK_COUNT_WEIGHT * normalizedChunkCount(scoredChunks)
                + KEYWORD_MATCH_WEIGHT * keywordMatchRatio(context.queryText(), scoredChunks)
                + RECENCY_WEIGHT * recencyScore(scoredChunks);

        return Math.min(1.0, Math.max(0.0, total));
    }

    private double normalizedMeanFusionScore(List<ScoredChunk> scoredChunks) {
        double mean = scoredChunks.stream().mapToDouble(ScoredChunk::fusionScore).average().orElse(0.0);
        return Math.min(1.0, mean / MAX_EXPECTED_FUSION_SCORE);
    }

    private double normalizedChunkCount(List<ScoredChunk> scoredChunks) {
        return Math.min(1.0, (double) scoredChunks.size() / EXPECTED_CHUNK_COUNT);
    }

    /**
     * Token-overlap proxy for keyword relevance: the fraction of distinct
     * query tokens that appear anywhere in the combined retrieved content.
     * This is a simplification of BM25 term-frequency scoring, which would
     * require corpus-wide statistics not available at the domain layer.
     */
    private double keywordMatchRatio(String queryText, List<ScoredChunk> scoredChunks) {
        Set<String> queryTokens = tokenize(queryText);
        if (queryTokens.isEmpty()) {
            return 0.0;
        }
        String combinedContent = scoredChunks.stream()
                .map(scoredChunk -> scoredChunk.chunk().getContent())
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
        long matched = queryTokens.stream().filter(combinedContent::contains).count();
        return (double) matched / queryTokens.size();
    }

    private double recencyScore(List<ScoredChunk> scoredChunks) {
        LocalDate mostRecent = scoredChunks.stream()
                .map(ScoredChunk::lastUpdated)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        long daysOld = ChronoUnit.DAYS.between(mostRecent, LocalDate.now());
        if (daysOld <= RECENCY_FULL_SCORE_DAYS) {
            return 1.0;
        }
        if (daysOld >= RECENCY_ZERO_SCORE_DAYS) {
            return 0.0;
        }
        double range = RECENCY_ZERO_SCORE_DAYS - RECENCY_FULL_SCORE_DAYS;
        return 1.0 - ((daysOld - RECENCY_FULL_SCORE_DAYS) / range);
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(TOKEN_DELIMITER.split(text.toLowerCase(Locale.ROOT)))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }
}