package com.neaz.travel.visa.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Qdrant gRPC client bean.
 * The application uses the raw gRPC client rather than Spring AI's QdrantVectorStore
 * so that metadata filters can be applied before cosine ranking — a hard correctness requirement.
 */
@Configuration
public class VectorStoreConfig {

    @Bean(destroyMethod = "close")
    public QdrantClient qdrantClient(
            @Value("${qdrant.host}") String host,
            @Value("${qdrant.port}") int port) {
        return new QdrantClient(
                QdrantGrpcClient.newBuilder(host, port, false).build()
        );
    }
}