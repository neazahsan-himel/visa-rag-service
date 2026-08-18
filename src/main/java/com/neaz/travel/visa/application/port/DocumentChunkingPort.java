package com.neaz.travel.visa.application.port;

import com.neaz.travel.visa.domain.model.Chunk;
import com.neaz.travel.visa.domain.model.DocumentMetadata;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port for splitting a document's extracted text into {@link Chunk}
 * segments suitable for embedding. Implemented in {@code infrastructure/document}
 * as a sliding-window, sentence-boundary-aware chunker; the application layer
 * depends only on this contract.
 */
public interface DocumentChunkingPort {

    /**
     * Splits {@code text} into ordered chunks belonging to the document
     * identified by {@code documentId}.
     *
     * @param text       the document's full extracted plain text
     * @param documentId the id of the {@code Document} these chunks belong to
     * @param metadata   the document's metadata, available to chunking strategies
     *                   that vary behaviour by visa type or source
     * @return chunks in document order, each carrying a freshly assigned id
     *         and Qdrant point id
     */
    List<Chunk> chunk(String text, UUID documentId, DocumentMetadata metadata);
}