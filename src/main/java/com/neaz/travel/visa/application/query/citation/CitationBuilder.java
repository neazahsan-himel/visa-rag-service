package com.neaz.travel.visa.application.query.citation;

import com.neaz.travel.visa.common.exception.DocumentNotFoundException;
import com.neaz.travel.visa.domain.model.Chunk;
import com.neaz.travel.visa.domain.model.Citation;
import com.neaz.travel.visa.domain.model.Document;
import com.neaz.travel.visa.domain.model.RetrievedContext;
import com.neaz.travel.visa.domain.model.ScoredChunk;
import com.neaz.travel.visa.domain.repository.DocumentRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds the {@link Citation} list for a {@link RetrievedContext}, numbering
 * each citation {@code [1]}, {@code [2]}, ... in {@code scoredChunks()} order
 * to match the bracket references {@code VisaPromptTemplate} instructs the
 * model to use — both classes must be given the same {@code RetrievedContext}
 * instance for the numbering to line up.
 *
 * <p>{@code Chunk}/{@code ScoredChunk} carry no document title or source URL,
 * so each chunk's parent {@link Document} is resolved via
 * {@link DocumentRepository}, cached per {@code build()} call to avoid
 * redundant lookups when multiple retrieved chunks share a document.
 */
@Component
public class CitationBuilder {

    private final DocumentRepository documentRepository;

    public CitationBuilder(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public List<Citation> build(RetrievedContext context) {
        Objects.requireNonNull(context, "context must not be null");

        Map<UUID, Document> documentCache = new HashMap<>();
        List<ScoredChunk> scoredChunks = context.scoredChunks();
        List<Citation> citations = new ArrayList<>(scoredChunks.size());

        for (int i = 0; i < scoredChunks.size(); i++) {
            ScoredChunk scoredChunk = scoredChunks.get(i);
            Chunk chunk = scoredChunk.chunk();
            Document document = documentCache.computeIfAbsent(chunk.getDocumentId(), this::requireDocument);

            citations.add(new Citation(
                    "[" + (i + 1) + "]",
                    document.getTitle(),
                    document.getMetadata().sourceUrl(),
                    scoredChunk.lastUpdated(),
                    chunk.getId()));
        }
        return citations;
    }

    private Document requireDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }
}