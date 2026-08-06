package com.neaz.travel.visa.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for the {@code document_chunks} table. The generated {@code
 * tsv} column is intentionally not mapped here — it is DB-computed and
 * read-only, and full-text search reads it via a native {@code ts_rank}
 * query rather than through this entity. {@code id} is app-supplied, same
 * rationale as {@link DocumentJpaEntity}.
 */
@Entity
@Table(name = "document_chunks")
public class ChunkJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(name = "qdrant_point_id", nullable = false, unique = true)
    private UUID qdrantPointId;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected ChunkJpaEntity() {
        // required by JPA
    }

    public ChunkJpaEntity(UUID id, UUID documentId, int chunkIndex, String content, int tokenCount,
                           UUID qdrantPointId) {
        this.id = id;
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.tokenCount = tokenCount;
        this.qdrantPointId = qdrantPointId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}