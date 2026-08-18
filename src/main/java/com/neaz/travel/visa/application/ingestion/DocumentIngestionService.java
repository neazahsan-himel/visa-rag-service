package com.neaz.travel.visa.application.ingestion;

import com.neaz.travel.visa.application.command.IngestDocumentCommand;
import com.neaz.travel.visa.application.port.DocumentChunkingPort;
import com.neaz.travel.visa.application.port.DocumentParsingPort;
import com.neaz.travel.visa.application.port.EmbeddingPort;
import com.neaz.travel.visa.application.port.VectorSearchPort;
import com.neaz.travel.visa.application.result.IngestionResult;
import com.neaz.travel.visa.common.exception.VisaRagException;
import com.neaz.travel.visa.domain.model.Chunk;
import com.neaz.travel.visa.domain.model.Document;
import com.neaz.travel.visa.domain.model.DocumentSource;
import com.neaz.travel.visa.domain.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
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
        String fileHash = sha256Hex(text);

        Optional<Document> existing = documentRepository.findByFileHash(fileHash);
        if (existing.isPresent()) {
            Document duplicate = existing.get();
            return new IngestionResult(duplicate.getId(), duplicate.getChunks().size(), IngestionResult.Status.DUPLICATE);
        }

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

        return new IngestionResult(documentId, chunks.size(), IngestionResult.Status.CREATED);
    }

    /**
     * Fingerprints the document by its extracted text (not its raw bytes,
     * which this layer never sees) so repeated ingestion of the same content
     * is detected as a duplicate rather than silently re-ingested.
     */
    private String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new VisaRagException("SHA-256 algorithm not available", e);
        }
    }
}