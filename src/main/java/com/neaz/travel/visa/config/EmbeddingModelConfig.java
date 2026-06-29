package com.neaz.travel.visa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures embedding model settings.
 * Spring AI auto-configures the {@code EmbeddingModel} bean via
 * {@code spring-ai-openai-spring-boot-starter}. This class exposes the active
 * model ID as a named bean for injection into adapters and audit logging.
 *
 * <p>To switch to Ollama, activate the {@code ollama} Spring profile and add
 * {@code spring-ai-ollama-spring-boot-starter} to the classpath.
 */
@Configuration
public class EmbeddingModelConfig {

    @Bean("embeddingModelId")
    public String embeddingModelId(
            @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String modelId) {
        return modelId;
    }
}