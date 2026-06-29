package com.neaz.travel.visa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures chat model settings.
 * Spring AI auto-configures the {@code ChatModel} bean via
 * {@code spring-ai-openai-spring-boot-starter}. This class exposes the active
 * model ID as a named bean for injection into the ChatAdapter and audit logging.
 *
 * <p>To switch to Ollama, activate the {@code ollama} Spring profile and add
 * {@code spring-ai-ollama-spring-boot-starter} to the classpath.
 */
@Configuration
public class ChatModelConfig {

    @Bean("chatModelId")
    public String chatModelId(
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String modelId) {
        return modelId;
    }
}