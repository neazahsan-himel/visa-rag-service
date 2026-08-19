package com.neaz.travel.visa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures embedding model settings.
 * Spring AI auto-configures the {@code EmbeddingModel} bean from whichever
 * provider {@code spring.ai.model.embedding} selects (openai or ollama). This
 * class exposes the active model ID as a named bean for injection into
 * adapters and audit logging.
 *
 * <p>Switch providers with {@code SPRING_PROFILES_ACTIVE=ollama} (see
 * {@code application-ollama.properties}); the nested placeholder below picks
 * up whichever provider's model property is actually set.
 */
@Configuration
public class EmbeddingModelConfig {

    @Bean("embeddingModelId")
    public String embeddingModelId(
            @Value("${spring.ai.ollama.embedding.options.model:${spring.ai.openai.embedding.options.model:text-embedding-3-small}}") String modelId) {
        return modelId;
    }
}