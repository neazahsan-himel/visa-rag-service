package com.neaz.travel.visa.infrastructure.vectorstore;

import com.neaz.travel.visa.application.port.VectorSearchPort;
import com.neaz.travel.visa.common.exception.VisaRagException;
import com.neaz.travel.visa.domain.model.Chunk;
import com.neaz.travel.visa.domain.model.DocumentMetadata;
import com.neaz.travel.visa.domain.model.MetadataFilter;
import com.neaz.travel.visa.domain.model.ScoredChunk;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.Points.PointStruct;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Implements the vector-store half of retrieval and ingestion against
 * Qdrant's {@code visa_chunks} collection (schema created by
 * {@code QdrantCollectionInitializer}) via the raw gRPC client.
 */
@Component
public class QdrantVectorStoreAdapter implements VectorSearchPort {

    private static final String COLLECTION_NAME = "visa_chunks";

    private final QdrantClient qdrantClient;

    public QdrantVectorStoreAdapter(QdrantClient qdrantClient) {
        this.qdrantClient = qdrantClient;
    }

    @Override
    public List<ScoredChunk> search(float[] queryVector, MetadataFilter filter, int topK) {
        // TODO(Sprint 5): pre-filtered cosine similarity search, consumed by
        // HybridRetriever alongside DocumentRepository.fullTextSearch. Not
        // needed until the query pipeline exists; this sprint only wires
        // ingestion (upsert).
        throw new UnsupportedOperationException(
                "QdrantVectorStoreAdapter.search is not implemented until Sprint 5's HybridRetriever");
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