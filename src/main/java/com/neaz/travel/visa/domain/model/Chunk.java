package com.neaz.travel.visa.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A single text segment produced by splitting a {@link Document} for embedding.
 * Identity is defined by {@code id}; two chunks with the same content but
 * different identifiers are distinct entities.
 */
public final class Chunk {

    private final UUID id;
    private final UUID documentId;
    private final int chunkIndex;
    private final String content;
    private final int tokenCount;
    private final UUID qdrantPointId;

    public Chunk(UUID id, UUID documentId, int chunkIndex, String content, int tokenCount, UUID qdrantPointId) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.documentId = Objects.requireNonNull(documentId, "documentId must not be null");
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (tokenCount < 0) {
            throw new IllegalArgumentException("tokenCount must not be negative");
        }
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.tokenCount = tokenCount;
        this.qdrantPointId = Objects.requireNonNull(qdrantPointId, "qdrantPointId must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public UUID getQdrantPointId() {
        return qdrantPointId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Chunk other)) {
            return false;
        }
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Chunk{id=%s, documentId=%s, chunkIndex=%d, tokenCount=%d}"
                .formatted(id, documentId, chunkIndex, tokenCount);
    }
}