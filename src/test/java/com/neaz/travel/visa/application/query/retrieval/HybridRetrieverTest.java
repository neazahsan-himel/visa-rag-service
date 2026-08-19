package com.neaz.travel.visa.application.query.retrieval;

import com.neaz.travel.visa.application.port.EmbeddingPort;
import com.neaz.travel.visa.application.port.VectorSearchPort;
import com.neaz.travel.visa.domain.model.Chunk;
import com.neaz.travel.visa.domain.model.MetadataFilter;
import com.neaz.travel.visa.domain.model.RetrievedContext;
import com.neaz.travel.visa.domain.model.ScoredChunk;
import com.neaz.travel.visa.domain.model.VisaQuery;
import com.neaz.travel.visa.domain.repository.DocumentRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link HybridRetriever}'s wiring to both retrieval ports and its
 * Reciprocal Rank Fusion merge, which operates purely on each path's rank
 * position rather than the raw scores the mocked ports return.
 */
class HybridRetrieverTest {

    private static final int TOP_K_PER_PATH = 20;
    private static final int RRF_K = 60;
    private static final MetadataFilter FILTER = new MetadataFilter("BD", "IN", "TOURIST");
    private static final String QUERY_TEXT = "tourist visa fee India";
    private static final double DELTA = 1e-9;

    private final EmbeddingPort embeddingPort = mock(EmbeddingPort.class);
    private final VectorSearchPort vectorSearchPort = mock(VectorSearchPort.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final HybridRetriever hybridRetriever =
            new HybridRetriever(embeddingPort, vectorSearchPort, documentRepository);

    @Test
    void retrieve_embedsQueryTextAndDelegatesToBothPathsWithTopK() {
        float[] queryVector = {0.1f, 0.2f, 0.3f};
        when(embeddingPort.embed(QUERY_TEXT)).thenReturn(queryVector);
        when(vectorSearchPort.search(queryVector, FILTER, TOP_K_PER_PATH)).thenReturn(List.of());
        when(documentRepository.fullTextSearch(QUERY_TEXT, FILTER, TOP_K_PER_PATH)).thenReturn(List.of());

        hybridRetriever.retrieve(new VisaQuery(QUERY_TEXT, FILTER));

        verify(embeddingPort).embed(QUERY_TEXT);
        verify(vectorSearchPort).search(queryVector, FILTER, TOP_K_PER_PATH);
        verify(documentRepository).fullTextSearch(QUERY_TEXT, FILTER, TOP_K_PER_PATH);
    }

    @Test
    void retrieve_returnsContextCarryingTheOriginalQueryText() {
        stubEmptyPaths();

        RetrievedContext context = hybridRetriever.retrieve(new VisaQuery(QUERY_TEXT, FILTER));

        assertEquals(QUERY_TEXT, context.queryText());
    }

    @Test
    void retrieve_withNoResultsFromEitherPath_returnsEmptyScoredChunks() {
        stubEmptyPaths();

        RetrievedContext context = hybridRetriever.retrieve(new VisaQuery(QUERY_TEXT, FILTER));

        assertTrue(context.scoredChunks().isEmpty());
    }

    @Test
    void retrieve_chunkRankedInBothPaths_outranksChunkFromOnlyOnePathViaCombinedRrfScore() {
        ScoredChunk chunkA = scoredChunk();
        ScoredChunk chunkB = scoredChunk();
        ScoredChunk chunkC = scoredChunk();
        // Vector path: A (rank 1), B (rank 2). Keyword path: B (rank 1), C (rank 2).
        // B accumulates contributions from both paths and should outrank A despite
        // never being ranked #1 on either individual path.
        stubPaths(List.of(chunkA, chunkB), List.of(chunkB, chunkC));

        RetrievedContext context = hybridRetriever.retrieve(new VisaQuery(QUERY_TEXT, FILTER));

        List<UUID> orderedIds = context.scoredChunks().stream()
                .map(sc -> sc.chunk().getId())
                .toList();
        assertEquals(List.of(chunkB.chunk().getId(), chunkA.chunk().getId(), chunkC.chunk().getId()), orderedIds);
    }

    @Test
    void retrieve_chunkInBothPaths_isDeduplicatedIntoASingleEntryWithSummedRrfScore() {
        ScoredChunk chunkA = scoredChunk();
        ScoredChunk chunkB = scoredChunk();
        stubPaths(List.of(chunkA, chunkB), List.of(chunkB, chunkA));

        RetrievedContext context = hybridRetriever.retrieve(new VisaQuery(QUERY_TEXT, FILTER));

        assertEquals(2, context.scoredChunks().size());
        double expectedScore = 1.0 / (RRF_K + 1) + 1.0 / (RRF_K + 2);
        for (ScoredChunk merged : context.scoredChunks()) {
            assertEquals(expectedScore, merged.fusionScore(), DELTA);
        }
    }

    @Test
    void retrieve_chunkFoundOnOnlyOneVectorPath_scoresExactlyThatPathsRrfContribution() {
        ScoredChunk chunkA = scoredChunk();
        stubPaths(List.of(chunkA), List.of());

        RetrievedContext context = hybridRetriever.retrieve(new VisaQuery(QUERY_TEXT, FILTER));

        assertEquals(1, context.scoredChunks().size());
        assertEquals(1.0 / (RRF_K + 1), context.scoredChunks().get(0).fusionScore(), DELTA);
    }

    @Test
    void retrieve_preservesOriginalChunkAndLastUpdatedInMergedResult() {
        ScoredChunk chunkA = scoredChunk();
        stubPaths(List.of(chunkA), List.of());

        RetrievedContext context = hybridRetriever.retrieve(new VisaQuery(QUERY_TEXT, FILTER));

        ScoredChunk merged = context.scoredChunks().get(0);
        assertEquals(chunkA.chunk(), merged.chunk());
        assertEquals(chunkA.lastUpdated(), merged.lastUpdated());
    }

    private void stubEmptyPaths() {
        stubPaths(List.of(), List.of());
    }

    private void stubPaths(List<ScoredChunk> vectorRanked, List<ScoredChunk> keywordRanked) {
        float[] queryVector = {0.1f, 0.2f, 0.3f};
        when(embeddingPort.embed(QUERY_TEXT)).thenReturn(queryVector);
        when(vectorSearchPort.search(eq(queryVector), eq(FILTER), eq(TOP_K_PER_PATH))).thenReturn(vectorRanked);
        when(documentRepository.fullTextSearch(QUERY_TEXT, FILTER, TOP_K_PER_PATH)).thenReturn(keywordRanked);
    }

    private ScoredChunk scoredChunk() {
        Chunk chunk = new Chunk(UUID.randomUUID(), UUID.randomUUID(), 0, "chunk content", 20, UUID.randomUUID());
        return new ScoredChunk(chunk, 0.05, LocalDate.now());
    }
}
