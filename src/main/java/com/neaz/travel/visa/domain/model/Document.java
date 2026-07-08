package com.neaz.travel.visa.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for an ingested official document and its chunks.
 * Identity is defined by {@code id}.
 */
public final class Document {

    private final UUID id;
    private final String title;
    private final String fileHash;
    private final DocumentMetadata metadata;
    private final List<Chunk> chunks;
    private final Instant ingestedAt;
    private boolean active;

    public Document(UUID id, String title, String fileHash, DocumentMetadata metadata,
                     List<Chunk> chunks, Instant ingestedAt, boolean active) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (fileHash == null || fileHash.isBlank()) {
            throw new IllegalArgumentException("fileHash must not be blank");
        }
        this.title = title;
        this.fileHash = fileHash;
        this.metadata = Objects.requireNonNull(metadata, "metadata must not be null");
        this.chunks = List.copyOf(Objects.requireNonNull(chunks, "chunks must not be null"));
        this.ingestedAt = Objects.requireNonNull(ingestedAt, "ingestedAt must not be null");
        this.active = active;
    }

    /**
     * Marks this document as superseded. The row is retained for audit
     * history rather than deleted.
     */
    public void deactivate() {
        this.active = false;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getFileHash() {
        return fileHash;
    }

    public DocumentMetadata getMetadata() {
        return metadata;
    }

    public List<Chunk> getChunks() {
        return chunks;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Document other)) {
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
        return "Document{id=%s, title='%s', chunks=%d, active=%s}"
                .formatted(id, title, chunks.size(), active);
    }
}