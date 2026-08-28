package com.neaz.travel.visa.application.port;

import com.neaz.travel.visa.domain.model.Chunk;
import com.neaz.travel.visa.domain.model.DocumentMetadata;
import com.neaz.travel.visa.domain.model.MetadataFilter;
import com.neaz.travel.visa.domain.model.ScoredChunk;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port for the vector-store half of retrieval and ingestion.
 * Implemented in {@code infrastructure/vectorstore} against Qdrant; the
 * application layer depends only on this contract.
 */
public interface VectorSearchPort {

    /**
     * Performs a metadata-filtered cosine similarity search. The filter must
     * be applied as a payload filter before scoring — an unfiltered search is
     * a defect.
     *
     * @param queryVector the query embedding
     * @param filter      the passport/destination/visa-type scope to apply before scoring
     * @param topK        maximum number of results to return
     * @return chunks ranked by similarity, highest first
     */
    List<ScoredChunk> search(float[] queryVector, MetadataFilter filter, int topK);

    /**
     * Inserts or replaces the vector and payload for one chunk.
     *
     * @param chunk    the chunk being indexed; supplies the point id, document id, and chunk index
     * @param vector   the chunk's content embedding
     * @param metadata the parent document's metadata, supplying the payload's
     *                 passport/destination/visa-type/last-updated fields
     */
    void upsert(Chunk chunk, float[] vector, DocumentMetadata metadata);

    /**
     * Marks every indexed chunk of a document inactive via a payload-only
     * update — no vectors are re-upserted. Used when a document is
     * superseded by a newer version, so its chunks stop being retrievable
     * without needing to be re-embedded or deleted.
     *
     * @param documentId the document whose chunks should be marked inactive
     */
    void deactivateDocument(UUID documentId);
}