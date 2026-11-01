package com.neaz.travel.visa.application.ingestion;

import com.neaz.travel.visa.application.command.IngestDocumentCommand;
import com.neaz.travel.visa.application.port.DocumentChunkingPort;
import com.neaz.travel.visa.application.port.DocumentParsingPort;
import com.neaz.travel.visa.application.port.EmbeddingPort;
import com.neaz.travel.visa.application.port.VectorSearchPort;
import com.neaz.travel.visa.application.result.IngestionResult;
import com.neaz.travel.visa.common.util.Sha256Hasher;
import com.neaz.travel.visa.domain.model.Chunk;
import com.neaz.travel.visa.domain.model.Document;
import com.neaz.travel.visa.domain.model.DocumentMetadata;
import com.neaz.travel.visa.domain.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link DocumentIngestionService}'s dedup and version-supersession
 * behaviour. Supersession is keyed by {@code documentKey}, never by source
 * URL, because one official source can yield several distinct documents.
 */
class DocumentIngestionServiceTest {

    private static final String TEXT = "Tourist visa applicants must submit a bank statement.";
    private static final String DOCUMENT_KEY = "in/bd/tourist/required-documents";
    private static final String SHARED_SOURCE_URL = "https://hci-dhaka.example/visa-checklist.pdf";

    private final DocumentParsingPort documentParsingPort = mock(DocumentParsingPort.class);
    private final DocumentChunkingPort documentChunkingPort = mock(DocumentChunkingPort.class);
    private final EmbeddingPort embeddingPort = mock(EmbeddingPort.class);
    private final VectorSearchPort vectorSearchPort = mock(VectorSearchPort.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentIngestionService ingestionService = new DocumentIngestionService(
            documentParsingPort, documentChunkingPort, embeddingPort, vectorSearchPort, documentRepository);

    @BeforeEach
    void stubPipeline() {
        when(documentParsingPort.parse(any())).thenReturn(TEXT);
        when(documentChunkingPort.chunk(eq(TEXT), any(UUID.class), any(DocumentMetadata.class)))
                .thenAnswer(invocation -> List.of(new Chunk(UUID.randomUUID(), invocation.getArgument(1), 0,
                        TEXT, 10, UUID.randomUUID())));
        when(embeddingPort.embed(TEXT)).thenReturn(new float[] {0.1f, 0.2f});
        when(documentRepository.findByFileHash(any())).thenReturn(Optional.empty());
    }

    @Test
    void ingest_whenNoActiveDocumentHasTheKey_savesWithoutSuperseding() {
        when(documentRepository.findActiveByDocumentKey(DOCUMENT_KEY)).thenReturn(Optional.empty());

        IngestionResult result = ingestionService.ingest(command(DOCUMENT_KEY));

        assertEquals(IngestionResult.Status.CREATED, result.status());
        verify(documentRepository).save(any(Document.class));
        verify(documentRepository, never()).replace(any(), any());
        verify(vectorSearchPort, never()).deactivateDocument(any());
    }

    @Test
    void ingest_whenActiveDocumentHasTheSameKey_replacesItAndDeactivatesItsVectorsAfterUpsert() {
        Document previous = existingDocument(DOCUMENT_KEY, "hash-of-older-content");
        when(documentRepository.findActiveByDocumentKey(DOCUMENT_KEY)).thenReturn(Optional.of(previous));

        IngestionResult result = ingestionService.ingest(command(DOCUMENT_KEY));

        assertEquals(IngestionResult.Status.CREATED, result.status());
        assertFalse(previous.isActive());
        verify(documentRepository, never()).save(any(Document.class));
        InOrder order = inOrder(documentRepository, vectorSearchPort);
        order.verify(documentRepository).replace(eq(previous), any(Document.class));
        order.verify(vectorSearchPort).upsert(any(), any(), any());
        order.verify(vectorSearchPort).deactivateDocument(previous.getId());
    }

    @Test
    void ingest_whenContentAlreadyIngested_returnsDuplicateWithoutSuperseding() {
        Document existing = existingDocument(DOCUMENT_KEY, Sha256Hasher.hex(TEXT));
        when(documentRepository.findByFileHash(Sha256Hasher.hex(TEXT))).thenReturn(Optional.of(existing));

        IngestionResult result = ingestionService.ingest(command(DOCUMENT_KEY));

        assertEquals(IngestionResult.Status.DUPLICATE, result.status());
        assertEquals(existing.getId(), result.documentId());
        verify(documentRepository, never()).findActiveByDocumentKey(any());
        verify(documentRepository, never()).save(any());
        verify(documentRepository, never()).replace(any(), any());
    }

    private IngestDocumentCommand command(String documentKey) {
        return new IngestDocumentCommand("India/Tourist/hci-dhaka-visa-documents.md",
                "India Tourist Visa - Required Documents", metadata(documentKey));
    }

    private Document existingDocument(String documentKey, String fileHash) {
        return new Document(UUID.randomUUID(), "India Tourist Visa - Required Documents", fileHash,
                metadata(documentKey), List.of(), Instant.now(), true);
    }

    private DocumentMetadata metadata(String documentKey) {
        return new DocumentMetadata(documentKey, "IN", "BD", "TOURIST", SHARED_SOURCE_URL,
                LocalDate.of(2026, 8, 6), null);
    }
}
