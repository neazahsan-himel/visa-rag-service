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

        Optional<Document> previousVersion =
                documentRepository.findActiveByDocumentKey(command.metadata().documentKey());

        UUID documentId = UUID.randomUUID();
        List<Chunk> chunks = documentChunkingPort.chunk(text, documentId, command.metadata());
        List<float[]> vectors = chunks.stream()
                .map(chunk -> embeddingPort.embed(chunk.getContent()))
                .toList();

        Document document = new Document(
                documentId, command.title(), fileHash, command.metadata(), chunks, Instant.now(), true);

        // Retire the prior version of the same logical document in both stores
        // so no query can retrieve superseded and current content at once.
        // PostgreSQL swaps the two atomically; Qdrant is updated after the new
        // points exist so the document is never unretrievable in between.
        previousVersion.ifPresentOrElse(previous -> {
            previous.deactivate();
            documentRepository.replace(previous, document);
        }, () -> documentRepository.save(document));

        for (int i = 0; i < chunks.size(); i++) {
            vectorSearchPort.upsert(chunks.get(i), vectors.get(i), command.metadata());
        }

        previousVersion.ifPresent(previous -> vectorSearchPort.deactivateDocument(previous.getId()));

        return new IngestionResult(documentId, chunks.size(), IngestionResult.Status.CREATED);
    }
}