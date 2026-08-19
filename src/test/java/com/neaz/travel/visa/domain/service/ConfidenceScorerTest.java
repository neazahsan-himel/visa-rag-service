package com.neaz.travel.visa.domain.service;

import com.neaz.travel.visa.domain.model.Chunk;
import com.neaz.travel.visa.domain.model.RetrievedContext;
import com.neaz.travel.visa.domain.model.ScoredChunk;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link ConfidenceScorer}'s four weighted factors (fusion score 0.50,
 * chunk count 0.20, keyword match 0.20, recency 0.10) both in aggregate and in
 * isolation. Isolation tests pin the other three factors at their maximum
 * contribution so the factor under test can be checked against an exact
 * hand-computed total.
 */
class ConfidenceScorerTest {

    private static final double CONFIDENCE_THRESHOLD = 0.60;
    private static final String QUERY_TEXT = "tourist visa fee India";
    private static final String FULL_MATCH_CONTENT =
            "The tourist visa fee for India is 80 USD, processed within 5 business days.";
    private static final double DELTA = 1e-6;

    private final ConfidenceScorer confidenceScorer = new ConfidenceScorer();

    @Test
    void score_withStrongRecentMatchingChunks_returnsScoreAboveThreshold() {
        RetrievedContext context = neutralContext(10, 0.05, FULL_MATCH_CONTENT, LocalDate.now());

        double score = confidenceScorer.score(context);

        assertTrue(score >= CONFIDENCE_THRESHOLD, "expected score >= " + CONFIDENCE_THRESHOLD + " but was " + score);
    }

    @Test
    void score_withSparseStaleUnrelatedChunk_returnsScoreBelowThreshold() {
        List<ScoredChunk> scoredChunks = List.of(
                scoredChunk("Unrelated embassy holiday closure schedule for consular staff.",
                        0, 0.001, LocalDate.now().minusYears(6)));
        RetrievedContext context = new RetrievedContext(QUERY_TEXT, scoredChunks);

        double score = confidenceScorer.score(context);

        assertTrue(score < CONFIDENCE_THRESHOLD, "expected score < " + CONFIDENCE_THRESHOLD + " but was " + score);
    }

    @Test
    void score_withEmptyRetrievedContext_returnsZero() {
        RetrievedContext context = new RetrievedContext(QUERY_TEXT, List.of());

        double score = confidenceScorer.score(context);

        assertEquals(0.0, score);
    }

    @Test
    void score_withNullContext_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> confidenceScorer.score(null));
    }

    // --- Fusion score factor (weight 0.50), other three pinned at full contribution ---

    @Test
    void score_fusionScoreBelowMax_scalesLinearly() {
        RetrievedContext context = neutralContext(10, 0.025, FULL_MATCH_CONTENT, LocalDate.now());

        double score = confidenceScorer.score(context);

        assertEquals(0.75, score, DELTA);
    }

    @Test
    void score_fusionScoreAtMax_returnsFullContribution() {
        RetrievedContext context = neutralContext(10, 0.05, FULL_MATCH_CONTENT, LocalDate.now());

        double score = confidenceScorer.score(context);

        assertEquals(1.0, score, DELTA);
    }

    @Test
    void score_fusionScoreAboveMax_isCappedAtFullContribution() {
        RetrievedContext context = neutralContext(10, 0.10, FULL_MATCH_CONTENT, LocalDate.now());

        double score = confidenceScorer.score(context);

        assertEquals(1.0, score, DELTA);
    }

    // --- Chunk count factor (weight 0.20), other three pinned at full contribution ---

    @Test
    void score_chunkCountBelowExpected_scalesLinearly() {
        RetrievedContext context = neutralContext(1, 0.05, FULL_MATCH_CONTENT, LocalDate.now());

        double score = confidenceScorer.score(context);

        assertEquals(0.82, score, DELTA);
    }

    @Test
    void score_chunkCountAtExpected_returnsFullContribution() {
        RetrievedContext context = neutralContext(10, 0.05, FULL_MATCH_CONTENT, LocalDate.now());

        double score = confidenceScorer.score(context);

        assertEquals(1.0, score, DELTA);
    }

    @Test
    void score_chunkCountAboveExpected_isCappedAtFullContribution() {
        RetrievedContext context = neutralContext(20, 0.05, FULL_MATCH_CONTENT, LocalDate.now());

        double score = confidenceScorer.score(context);

        assertEquals(1.0, score, DELTA);
    }

    // --- Keyword match factor (weight 0.20), other three pinned at full contribution ---

    @Test
    void score_keywordMatchNoOverlap_contributesZero() {
        RetrievedContext context = neutralContext(10, 0.05, "Unrelated content about consular holidays.", LocalDate.now());

        double score = confidenceScorer.score(context);

        assertEquals(0.80, score, DELTA);
    }

    @Test
    void score_keywordMatchPartialOverlap_scalesByMatchedTokenRatio() {
        // Matches 2 of 4 query tokens ("tourist", "visa"): 4 tokens total in "tourist visa fee India".
        RetrievedContext context = neutralContext(10, 0.05, "The tourist visa process takes a few weeks.", LocalDate.now());

        double score = confidenceScorer.score(context);

        assertEquals(0.90, score, DELTA);
    }

    @Test
    void score_keywordMatchFullOverlap_contributesFullWeight() {
        RetrievedContext context = neutralContext(10, 0.05, FULL_MATCH_CONTENT, LocalDate.now());

        double score = confidenceScorer.score(context);

        assertEquals(1.0, score, DELTA);
    }

    // --- Recency factor (weight 0.10), other three pinned at full contribution ---

    @Test
    void score_recencyAtFullScoreBoundary_returnsFullContribution() {
        RetrievedContext context = neutralContext(10, 0.05, FULL_MATCH_CONTENT, LocalDate.now().minusDays(365));

        double score = confidenceScorer.score(context);

        assertEquals(1.0, score, DELTA);
    }

    @Test
    void score_recencyAtMidpoint_returnsHalfContribution() {
        RetrievedContext context = neutralContext(10, 0.05, FULL_MATCH_CONTENT, LocalDate.now().minusDays(1095));

        double score = confidenceScorer.score(context);

        assertEquals(0.95, score, DELTA);
    }

    @Test
    void score_recencyAtZeroScoreBoundary_returnsZeroContribution() {
        RetrievedContext context = neutralContext(10, 0.05, FULL_MATCH_CONTENT, LocalDate.now().minusDays(1825));

        double score = confidenceScorer.score(context);

        assertEquals(0.90, score, DELTA);
    }

    /**
     * Builds a context with {@code chunkCount} identical chunks sharing the given
     * fusion score, content, and last-updated date, so all chunks contribute the
     * same values and the resulting factor is exact rather than averaged noise.
     */
    private RetrievedContext neutralContext(int chunkCount, double fusionScore, String content, LocalDate lastUpdated) {
        List<ScoredChunk> scoredChunks = IntStream.range(0, chunkCount)
                .mapToObj(index -> scoredChunk(content, index, fusionScore, lastUpdated))
                .collect(Collectors.toList());
        return new RetrievedContext(QUERY_TEXT, scoredChunks);
    }

    private ScoredChunk scoredChunk(String content, int chunkIndex, double fusionScore, LocalDate lastUpdated) {
        Chunk chunk = new Chunk(UUID.randomUUID(), UUID.randomUUID(), chunkIndex, content, 20, UUID.randomUUID());
        return new ScoredChunk(chunk, fusionScore, lastUpdated);
    }
}
