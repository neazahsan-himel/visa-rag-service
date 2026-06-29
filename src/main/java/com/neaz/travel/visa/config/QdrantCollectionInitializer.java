package com.neaz.travel.visa.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.PayloadSchemaType;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Collections.VectorsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the {@code visa_chunks} Qdrant collection and all payload indexes on startup
 * if they do not already exist. Runs once per application start via {@link ApplicationRunner}.
 */
@Component
public class QdrantCollectionInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QdrantCollectionInitializer.class);

    static final String COLLECTION_NAME = "visa_chunks";
    private static final int EMBEDDING_DIMENSION = 1536;

    private final QdrantClient qdrantClient;

    public QdrantCollectionInitializer(QdrantClient qdrantClient) {
        this.qdrantClient = qdrantClient;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean exists = qdrantClient.listCollectionsAsync().get()
                .stream()
                .anyMatch(c -> c.getName().equals(COLLECTION_NAME));

        if (exists) {
            log.info("Qdrant collection '{}' already exists — skipping creation", COLLECTION_NAME);
            return;
        }

        log.info("Creating Qdrant collection '{}'", COLLECTION_NAME);

        qdrantClient.createCollectionAsync(
                COLLECTION_NAME,
                VectorsConfig.newBuilder()
                        .setParams(VectorParams.newBuilder()
                                .setSize(EMBEDDING_DIMENSION)
                                .setDistance(Distance.Cosine)
                                .build())
                        .build()
        ).get();

        createPayloadIndexes();

        log.info("Qdrant collection '{}' created with all payload indexes", COLLECTION_NAME);
    }

    private void createPayloadIndexes() throws Exception {
        createKeywordIndex("chunk_id");
        createKeywordIndex("document_id");
        createKeywordIndex("passport_iso");
        createKeywordIndex("destination_iso");
        createKeywordIndex("visa_type_code");
        createKeywordIndex("last_updated");
        createIntegerIndex("chunk_index");
    }

    private void createKeywordIndex(String fieldName) throws Exception {
        qdrantClient.createPayloadIndexAsync(
                COLLECTION_NAME, fieldName, PayloadSchemaType.Keyword, null, null, null
        ).get();
        log.debug("Created keyword index on '{}'", fieldName);
    }

    private void createIntegerIndex(String fieldName) throws Exception {
        qdrantClient.createPayloadIndexAsync(
                COLLECTION_NAME, fieldName, PayloadSchemaType.Integer, null, null, null
        ).get();
        log.debug("Created integer index on '{}'", fieldName);
    }
}