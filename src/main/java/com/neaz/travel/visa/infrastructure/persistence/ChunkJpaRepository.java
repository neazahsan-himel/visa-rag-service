package com.neaz.travel.visa.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for {@link ChunkJpaEntity}. Adds a native
 * full-text-search query since {@code ts_rank}, {@code @@}, and {@code
 * plainto_tsquery} over the GIN-indexed {@code tsv} column are not
 * expressible in JPQL.
 */
public interface ChunkJpaRepository extends JpaRepository<ChunkJpaEntity, UUID> {

    /**
     * Returns every chunk belonging to a document, in original chunking
     * order. Used by {@code DocumentRepositoryAdapter} to reconstruct a
     * {@link com.neaz.travel.visa.domain.model.Document}'s chunk list.
     */
    List<ChunkJpaEntity> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    /**
     * Metadata-filtered full-text search, joined through {@code documents}
     * to apply {@code passport_iso}, destination (via {@code countries}),
     * and optional visa type (via {@code visa_types}) <em>before</em> {@code
     * ts_rank} scoring. Only chunks belonging to active documents are
     * eligible.
     */
    @Query(value = """
            SELECT dc.id AS id,
                   dc.document_id AS documentId,
                   dc.chunk_index AS chunkIndex,
                   dc.content AS content,
                   dc.token_count AS tokenCount,
                   dc.qdrant_point_id AS qdrantPointId,
                   d.last_updated AS lastUpdated,
                   ts_rank(dc.tsv, plainto_tsquery('english', :queryText)) AS rank
            FROM document_chunks dc
            JOIN documents d ON d.id = dc.document_id
            JOIN countries c ON c.id = d.country_id
            JOIN visa_types vt ON vt.id = d.visa_type_id
            WHERE d.active = true
              AND d.passport_iso = :passportIso
              AND c.iso_code = :destinationIso
              AND (CAST(:visaTypeCode AS VARCHAR) IS NULL OR vt.code = :visaTypeCode)
              AND dc.tsv @@ plainto_tsquery('english', :queryText)
            ORDER BY rank DESC
            LIMIT :topK
            """, nativeQuery = true)
    List<ChunkFullTextSearchProjection> searchFullText(@Param("queryText") String queryText,
                                                         @Param("passportIso") String passportIso,
                                                         @Param("destinationIso") String destinationIso,
                                                         @Param("visaTypeCode") String visaTypeCode,
                                                         @Param("topK") int topK);

    /**
     * Row shape returned by {@link #searchFullText}. Carries {@code
     * lastUpdated} from the joined {@code documents} row and the computed
     * {@code ts_rank} score, neither of which belongs on {@link
     * ChunkJpaEntity} itself.
     */
    interface ChunkFullTextSearchProjection {

        UUID getId();

        UUID getDocumentId();

        int getChunkIndex();

        String getContent();

        int getTokenCount();

        UUID getQdrantPointId();

        LocalDate getLastUpdated();

        double getRank();
    }
}