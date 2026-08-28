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
import com.neaz.travel.visa.domain.model.DocumentSource;
import com.neaz.travel.visa.domain.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the document ingestion use case: parse, chunk, embed, persist
 * to PostgreSQL, and upsert to Qdrant. Contains no parsing or I/O logic
 * itself — that lives entirely behind the application ports it depends on.
 */
@Service
public class DocumentIngestionService {

    private final DocumentParsingPort documentParsingPort;
    private final DocumentChunkingPort documentChunkingPort;
    private final EmbeddingPort embeddingPort;
    private final VectorSearchPort vectorSearchPort;
    private final DocumentRepository documentRepository;

    public DocumentIngestionService(
            DocumentParsingPort documentParsingPort,
            DocumentChunkingPort documentChunkingPort,
            EmbeddingPort embeddingPort,
            VectorSearchPort vectorSearchPort,
            DocumentRepository documentRepository) {
        this.documentParsingPort = documentParsingPort;
        this.documentChunkingPort = documentChunkingPort;
        this.embeddingPort = embeddingPort;
        this.vectorSearchPort = vectorSearchPort;
        this.documentRepository = documentRepository;
    }

    public IngestionResult ingest(IngestDocumentCommand command) {
        String text = documentParsingPort.parse(new DocumentSource(command.path()));
        String fileHash = Sha256Hasher.hex(text);

        Optional<Document> existing = documentRepository.findByFileHash(fileHash);
        if (existing.isPresent()) {
            Document duplicate = existing.get();
            return new IngestionResult(duplicate.getId(), duplicate.getChunks().size(), IngestionResult.Status.DUPLICATE);
        }

        String sourceUrl = command.metadata().sourceUrl();
        Optional<Document> previousVersion = sourceUrl == null
                ? Optional.empty()
                : documentRepository.findActiveBySourceUrl(sourceUrl);

        UUID documentId = UUID.randomUUID();
        List<Chunk> chunks = documentChunkingPort.chunk(text, documentId, command.metadata());
        List<float[]> vectors = chunks.stream()
                .map(chunk -> embeddingPort.embed(chunk.getContent()))
                .toList();

        Document document = new Document(
                documentId, command.title(), fileHash, command.metadata(), chunks, Instant.now(), true);
        documentRepository.save(document);

        for (int i = 0; i < chunks.size(); i++) {
            vectorSearchPort.upsert(chunks.get(i), vectors.get(i), command.metadata());
        }

        // Retire the prior version in both stores so no query can retrieve
        // superseded and current content for the same source at once.
        previousVersion.ifPresent(previous -> {
            previous.deactivate();
            documentRepository.save(previous);
            vectorSearchPort.deactivateDocument(previous.getId());
        });

        return new IngestionResult(documentId, chunks.size(), IngestionResult.Status.CREATED);
    }
}