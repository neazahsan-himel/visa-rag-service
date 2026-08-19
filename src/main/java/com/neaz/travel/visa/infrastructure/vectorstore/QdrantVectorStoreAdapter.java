package com.neaz.travel.visa.infrastructure.vectorstore;

import com.neaz.travel.visa.application.port.VectorSearchPort;
import com.neaz.travel.visa.common.exception.VisaRagException;
import com.neaz.travel.visa.domain.model.Chunk;
import com.neaz.travel.visa.domain.model.DocumentMetadata;
import com.neaz.travel.visa.domain.model.MetadataFilter;
import com.neaz.travel.visa.domain.model.ScoredChunk;
import com.neaz.travel.visa.infrastructure.persistence.ChunkJpaEntity;
import com.neaz.travel.visa.infrastructure.persistence.ChunkJpaRepository;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Implements the vector-store half of retrieval and ingestion against
 * Qdrant's {@code visa_chunks} collection (schema created by
 * {@code QdrantCollectionInitializer}) via the raw gRPC client.
 */
@Component
public class QdrantVectorStoreAdapter implements VectorSearchPort {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStoreAdapter.class);

    private static final String COLLECTION_NAME = "visa_chunks";

    /** Cross-cutting content that applies to every visa type; see {@code V8__add_general_visa_type.sql}. */
    private static final String GENERAL_VISA_TYPE_CODE = "GENERAL";

    private final QdrantClient qdrantClient;
    private final ChunkJpaRepository chunkJpaRepository;

    public QdrantVectorStoreAdapter(QdrantClient qdrantClient, ChunkJpaRepository chunkJpaRepository) {
        this.qdrantClient = qdrantClient;
        this.chunkJpaRepository = chunkJpaRepository;
    }

    /**
     * Qdrant's payload holds only filterable metadata, never chunk text (see
     * {@link #upsert}) — {@code content}/{@code tokenCount} live solely in
     * PostgreSQL. So each match is resolved to a full {@link Chunk} via
     * {@link ChunkJpaRepository}, keyed by the {@code chunk_id} payload field
     * written at upsert time. A match with no corresponding row (possible
     * since ingestion has no transaction spanning both stores) is logged and
     * skipped rather than failing the whole search.
     */
    @Override
    public List<ScoredChunk> search(float[] queryVector, MetadataFilter filter, int topK) {
        SearchPoints searchPoints = SearchPoints.newBuilder()
                .setCollectionName(COLLECTION_NAME)
                .addAllVector(toFloatList(queryVector))
                .setFilter(buildFilter(filter))
                .setLimit(topK)
                .setWithPayload(WithPayloadSelectorFactory.enable(true))
                .build();

        List<ScoredPoint> scoredPoints;
        try {
            scoredPoints = qdrantClient.searchAsync(searchPoints).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VisaRagException("Interrupted while searching Qdrant", e);
        } catch (ExecutionException e) {
            throw new VisaRagException("Failed to search Qdrant", e.getCause());
        }

        return hydrate(scoredPoints);
    }

    /**
     * Builds the payload filter applied before cosine scoring. When a
     * specific visa type is requested, {@code GENERAL} content always
     * matches alongside it — otherwise cross-cutting content would never
     * surface for any type-scoped query.
     */
    private Filter buildFilter(MetadataFilter filter) {
        Filter.Builder builder = Filter.newBuilder()
                .addMust(ConditionFactory.matchKeyword("passport_iso", filter.passportIso()))
                .addMust(ConditionFactory.matchKeyword("destination_iso", filter.destinationIso()));

        if (filter.visaTypeCode() != null) {
            Filter visaTypeOrGeneral = Filter.newBuilder()
                    .addShould(ConditionFactory.matchKeyword("visa_type_code", filter.visaTypeCode()))
                    .addShould(ConditionFactory.matchKeyword("visa_type_code", GENERAL_VISA_TYPE_CODE))
                    .build();
            builder.addMust(ConditionFactory.filter(visaTypeOrGeneral));
        }

        return builder.build();
    }

    private List<ScoredChunk> hydrate(List<ScoredPoint> scoredPoints) {
        List<UUID> chunkIds = scoredPoints.stream()
                .map(scoredPoint -> UUID.fromString(scoredPoint.getPayloadOrThrow("chunk_id").getStringValue()))
                .toList();

        Map<UUID, ChunkJpaEntity> chunksById = new HashMap<>();
        for (ChunkJpaEntity entity : chunkJpaRepository.findAllById(chunkIds)) {
            chunksById.put(entity.getId(), entity);
        }

        List<ScoredChunk> results = new ArrayList<>(scoredPoints.size());
        for (int i = 0; i < scoredPoints.size(); i++) {
            ScoredPoint scoredPoint = scoredPoints.get(i);
            UUID chunkId = chunkIds.get(i);
            ChunkJpaEntity entity = chunksById.get(chunkId);
            if (entity == null) {
                log.warn("Qdrant point references chunk {} with no matching PostgreSQL row; skipping", chunkId);
                continue;
            }

            Chunk chunk = new Chunk(entity.getId(), entity.getDocumentId(), entity.getChunkIndex(),
                    entity.getContent(), entity.getTokenCount(), entity.getQdrantPointId());
            LocalDate lastUpdated = LocalDate.parse(scoredPoint.getPayloadOrThrow("last_updated").getStringValue());
            results.add(new ScoredChunk(chunk, scoredPoint.getScore(), lastUpdated));
        }
        return results;
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float v : vector) {
            values.add(v);
        }
        return values;
    }

    @Override
    public void upsert(Chunk chunk, float[] vector, DocumentMetadata metadata) {
        PointStruct point = PointStruct.newBuilder()
                .setId(PointIdFactory.id(chunk.getQdrantPointId()))
                .setVectors(VectorsFactory.vectors(vector))
                .putPayload("chunk_id", ValueFactory.value(chunk.getId().toString()))
                .putPayload("document_id", ValueFactory.value(chunk.getDocumentId().toString()))
                .putPayload("passport_iso", ValueFactory.value(metadata.passportIso()))
                .putPayload("destination_iso", ValueFactory.value(metadata.destinationIso()))
                .putPayload("visa_type_code", ValueFactory.value(metadata.visaTypeCode()))
                .putPayload("last_updated", ValueFactory.value(metadata.lastUpdated().toString()))
                .putPayload("chunk_index", ValueFactory.value(chunk.getChunkIndex()))
                .build();

        try {
            qdrantClient.upsertAsync(COLLECTION_NAME, List.of(point)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VisaRagException("Interrupted while upserting chunk to Qdrant: " + chunk.getId(), e);
        } catch (ExecutionException e) {
            throw new VisaRagException("Failed to upsert chunk to Qdrant: " + chunk.getId(), e.getCause());
        }
    }
}