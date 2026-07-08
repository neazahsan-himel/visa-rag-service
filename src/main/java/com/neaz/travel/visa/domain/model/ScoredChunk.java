package com.neaz.travel.visa.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A {@link Chunk} paired with the score it received after Reciprocal Rank
 * Fusion of the vector-search and full-text-search retrieval paths.
 * {@code lastUpdated} is carried here — rather than on {@link Chunk} itself,
 * which mirrors the {@code document_chunks} table and has no such column —
 * because it is only available at retrieval time (from the Qdrant payload
 * or a join against {@code documents}), and {@code ConfidenceScorer} needs
 * it per retrieved chunk to score document recency.
 *
 * @param chunk       the retrieved chunk
 * @param fusionScore the RRF-merged relevance score
 * @param lastUpdated the last-updated date of the chunk's parent document
 */
public record ScoredChunk(Chunk chunk, double fusionScore, LocalDate lastUpdated) {

    public ScoredChunk {
        Objects.requireNonNull(chunk, "chunk must not be null");
        Objects.requireNonNull(lastUpdated, "lastUpdated must not be null");
    }
}