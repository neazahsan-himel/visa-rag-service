package com.neaz.travel.visa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures chat model settings.
 * Spring AI auto-configures the {@code ChatModel} bean from whichever provider
 * {@code spring.ai.model.chat} selects (openai or ollama). This class exposes
 * the active model ID as a named bean for injection into the ChatAdapter and
 * audit logging.
 *
 * <p>Switch providers with {@code SPRING_PROFILES_ACTIVE=ollama} (see
 * {@code application-ollama.properties}); the nested placeholder below picks
 * up whichever provider's model property is actually set.
 */
@Configuration
public class ChatModelConfig {

    @Bean("chatModelId")
    public String chatModelId(
            @Value("${spring.ai.ollama.chat.options.model:${spring.ai.openai.chat.options.model:gpt-4o-mini}}") String modelId) {
        return modelId;
    }
}