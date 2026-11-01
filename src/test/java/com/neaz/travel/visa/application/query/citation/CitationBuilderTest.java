package com.neaz.travel.visa.application.query.citation;

import com.neaz.travel.visa.common.exception.DocumentNotFoundException;
import com.neaz.travel.visa.domain.model.Chunk;
import com.neaz.travel.visa.domain.model.Citation;
import com.neaz.travel.visa.domain.model.Document;
import com.neaz.travel.visa.domain.model.DocumentMetadata;
import com.neaz.travel.visa.domain.model.RetrievedContext;
import com.neaz.travel.visa.domain.model.ScoredChunk;
import com.neaz.travel.visa.domain.repository.DocumentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link CitationBuilder}'s numbering, per-document lookup caching,
 * and the deliberate use of {@code ScoredChunk.lastUpdated} (retrieval-time,
 * per-chunk) rather than the parent {@code Document}'s own metadata date.
 */
class CitationBuilderTest {

    private static final String QUERY_TEXT = "tourist visa fee India";

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final CitationBuilder citationBuilder = new CitationBuilder(documentRepository);

    @Test
    void build_withMultipleChunksFromDifferentDocuments_returnsSequentiallyNumberedCitationsInOrder() {
        Document firstDocument = document("India Tourist Visa Checklist", "https://hci-dhaka.example/tourist.pdf");
        Document secondDocument = document("India Visa Fee Policy", "https://hci-dhaka.example/fees.pdf");
        when(documentRepository.findById(firstDocument.getId())).thenReturn(Optional.of(firstDocument));
        when(documentRepository.findById(secondDocument.getId())).thenReturn(Optional.of(secondDocument));

        ScoredChunk first = scoredChunk(firstDocument.getId(), LocalDate.of(2026, 1, 1));
        ScoredChunk second = scoredChunk(secondDocument.getId(), LocalDate.of(2026, 2, 1));
        RetrievedContext context = new RetrievedContext(QUERY_TEXT, List.of(first, second));

        List<Citation> citations = citationBuilder.build(context);

        assertEquals(2, citations.size());
        assertEquals("[1]", citations.get(0).ref());
        assertEquals("India Tourist Visa Checklist", citations.get(0).source());
        assertEquals("https://hci-dhaka.example/tourist.pdf", citations.get(0).url());
        assertEquals(first.chunk().getId(), citations.get(0).chunkId());
        assertEquals("[2]", citations.get(1).ref());
        assertEquals("India Visa Fee Policy", citations.get(1).source());
        assertEquals(second.chunk().getId(), citations.get(1).chunkId());
    }

    @Test
    void build_usesScoredChunkLastUpdatedNotDocumentMetadataLastUpdated() {
        Document document = document("India Tourist Visa Checklist", "https://hci-dhaka.example/tourist.pdf");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        LocalDate retrievalTimeLastUpdated = LocalDate.of(2026, 6, 15);
        ScoredChunk scoredChunk = scoredChunk(document.getId(), retrievalTimeLastUpdated);
        RetrievedContext context = new RetrievedContext(QUERY_TEXT, List.of(scoredChunk));

        List<Citation> citations = citationBuilder.build(context);

        assertEquals(retrievalTimeLastUpdated, citations.get(0).lastUpdated());
        assertTrue(document.getMetadata().lastUpdated().isBefore(retrievalTimeLastUpdated));
    }

    @Test
    void build_withMultipleChunksFromSameDocument_looksUpDocumentOnlyOnce() {
        Document document = document("India Visa Categories Overview", "https://hci-dhaka.example/categories.pdf");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        ScoredChunk first = scoredChunk(document.getId(), LocalDate.of(2026, 1, 1));
        ScoredChunk second = scoredChunk(document.getId(), LocalDate.of(2026, 1, 1));
        ScoredChunk third = scoredChunk(document.getId(), LocalDate.of(2026, 1, 1));
        RetrievedContext context = new RetrievedContext(QUERY_TEXT, List.of(first, second, third));

        List<Citation> citations = citationBuilder.build(context);

        assertEquals(3, citations.size());
        verify(documentRepository, times(1)).findById(document.getId());
    }

    @Test
    void build_withChunkReferencingMissingDocument_throwsDocumentNotFoundException() {
        UUID missingDocumentId = UUID.randomUUID();
        when(documentRepository.findById(missingDocumentId)).thenReturn(Optional.empty());
        ScoredChunk scoredChunk = scoredChunk(missingDocumentId, LocalDate.now());
        RetrievedContext context = new RetrievedContext(QUERY_TEXT, List.of(scoredChunk));

        DocumentNotFoundException exception = assertThrows(DocumentNotFoundException.class,
                () -> citationBuilder.build(context));

        assertEquals(missingDocumentId, exception.getDocumentId());
    }

    @Test
    void build_withEmptyRetrievedContext_returnsEmptyListWithoutRepositoryLookup() {
        RetrievedContext context = new RetrievedContext(QUERY_TEXT, List.of());

        List<Citation> citations = citationBuilder.build(context);

        assertTrue(citations.isEmpty());
        verify(documentRepository, never()).findById(any());
    }

    @Test
    void build_withNullContext_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> citationBuilder.build(null));
    }

    private Document document(String title, String sourceUrl) {
        DocumentMetadata metadata = new DocumentMetadata(
                "in/bd/tourist/" + UUID.randomUUID(), "IN", "BD", "TOURIST", sourceUrl,
                LocalDate.of(2025, 1, 1), "v1");
        return new Document(UUID.randomUUID(), title, "hash-" + UUID.randomUUID(), metadata,
                List.of(), Instant.now(), true);
    }

    private ScoredChunk scoredChunk(UUID documentId, LocalDate lastUpdated) {
        Chunk chunk = new Chunk(UUID.randomUUID(), documentId, 0, "chunk content", 20, UUID.randomUUID());
        return new ScoredChunk(chunk, 0.05, lastUpdated);
    }
}
