package com.neaz.travel.visa.application.query.retrieval;

import com.neaz.travel.visa.application.port.EmbeddingPort;
import com.neaz.travel.visa.application.port.VectorSearchPort;
import com.neaz.travel.visa.domain.model.RetrievedContext;
import com.neaz.travel.visa.domain.model.ScoredChunk;
import com.neaz.travel.visa.domain.model.VisaQuery;
import com.neaz.travel.visa.domain.repository.DocumentRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Performs hybrid retrieval: vector search and full-text search run
 * concurrently over the same {@link com.neaz.travel.visa.domain.model.MetadataFilter}-scoped
 * candidate set, then their rankings are merged with Reciprocal Rank Fusion
 * (RRF, {@code k = 60}) into a single relevance-ordered {@link RetrievedContext}.
 * RRF is applied to each path's <em>rank position</em>, not its raw score —
 * cosine similarity and {@code ts_rank} are on incomparable scales, which RRF
 * is specifically designed to avoid needing.
 *
 * <p><b>Note:</b> the caller-supplied {@code MetadataFilter} is passed through
 * to both {@link VectorSearchPort#search} and {@link DocumentRepository#fullTextSearch}
 * unchanged. Matching the {@code GENERAL} visa type as an always-include
 * fallback (alongside the requested type) must be implemented inside those
 * two adapters, not here — this class has no visibility into how either
 * filter is executed.
 */
@Component
public class HybridRetriever {

    private static final int RRF_K = 60;
    private static final int TOP_K_PER_PATH = 20;

    private final EmbeddingPort embeddingPort;
    private final VectorSearchPort vectorSearchPort;
    private final DocumentRepository documentRepository;

    public HybridRetriever(
            EmbeddingPort embeddingPort, VectorSearchPort vectorSearchPort, DocumentRepository documentRepository) {
        this.embeddingPort = embeddingPort;
        this.vectorSearchPort = vectorSearchPort;
        this.documentRepository = documentRepository;
    }

    public RetrievedContext retrieve(VisaQuery query) {
        float[] queryVector = embeddingPort.embed(query.queryText());

        CompletableFuture<List<ScoredChunk>> vectorRanked = CompletableFuture.supplyAsync(
                () -> vectorSearchPort.search(queryVector, query.filter(), TOP_K_PER_PATH));
        CompletableFuture<List<ScoredChunk>> keywordRanked = CompletableFuture.supplyAsync(
                () -> documentRepository.fullTextSearch(query.queryText(), query.filter(), TOP_K_PER_PATH));

        List<ScoredChunk> merged = fuse(vectorRanked.join(), keywordRanked.join());
        return new RetrievedContext(query.queryText(), merged);
    }

    private List<ScoredChunk> fuse(List<ScoredChunk> vectorRanked, List<ScoredChunk> keywordRanked) {
        Map<UUID, Double> rrfScores = new LinkedHashMap<>();
        Map<UUID, ScoredChunk> byId = new LinkedHashMap<>();
        accumulateRrf(vectorRanked, rrfScores, byId);
        accumulateRrf(keywordRanked, rrfScores, byId);

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .map(entry -> {
                    ScoredChunk original = byId.get(entry.getKey());
                    return new ScoredChunk(original.chunk(), entry.getValue(), original.lastUpdated());
                })
                .toList();
    }

    /**
     * Adds one ranked path's RRF contribution ({@code 1 / (k + rank)}, rank
     * starting at 1) to the running per-chunk total.
     */
    private void accumulateRrf(List<ScoredChunk> ranked, Map<UUID, Double> rrfScores, Map<UUID, ScoredChunk> byId) {
        for (int i = 0; i < ranked.size(); i++) {
            ScoredChunk scoredChunk = ranked.get(i);
            UUID chunkId = scoredChunk.chunk().getId();
            double contribution = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(chunkId, contribution, Double::sum);
            byId.putIfAbsent(chunkId, scoredChunk);
        }
    }
}