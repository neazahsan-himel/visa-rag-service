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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfidenceScorerTest {

    private static final double CONFIDENCE_THRESHOLD = 0.60;

    private final ConfidenceScorer confidenceScorer = new ConfidenceScorer();

    @Test
    void score_withStrongRecentMatchingChunks_returnsScoreAboveThreshold() {
        String queryText = "tourist visa fee India";
        List<ScoredChunk> scoredChunks = IntStream.range(0, 10)
                .mapToObj(index -> scoredChunk(
                        "The tourist visa fee for India is 80 USD, processed within 5 business days.",
                        index,
                        0.05,
                        LocalDate.now()))
                .collect(Collectors.toList());
        RetrievedContext context = new RetrievedContext(queryText, scoredChunks);

        double score = confidenceScorer.score(context);

        assertTrue(score >= CONFIDENCE_THRESHOLD, "expected score >= " + CONFIDENCE_THRESHOLD + " but was " + score);
    }

    @Test
    void score_withSparseStaleUnrelatedChunk_returnsScoreBelowThreshold() {
        String queryText = "tourist visa fee India";
        List<ScoredChunk> scoredChunks = List.of(
                scoredChunk("Unrelated embassy holiday closure schedule for consular staff.",
                        0, 0.001, LocalDate.now().minusYears(6)));
        RetrievedContext context = new RetrievedContext(queryText, scoredChunks);

        double score = confidenceScorer.score(context);

        assertTrue(score < CONFIDENCE_THRESHOLD, "expected score < " + CONFIDENCE_THRESHOLD + " but was " + score);
    }

    @Test
    void score_withEmptyRetrievedContext_returnsZero() {
        RetrievedContext context = new RetrievedContext("tourist visa fee India", List.of());

        double score = confidenceScorer.score(context);

        assertEquals(0.0, score);
    }

    private ScoredChunk scoredChunk(String content, int chunkIndex, double fusionScore, LocalDate lastUpdated) {
        Chunk chunk = new Chunk(UUID.randomUUID(), UUID.randomUUID(), chunkIndex, content, 20, UUID.randomUUID());
        return new ScoredChunk(chunk, fusionScore, lastUpdated);
    }
}