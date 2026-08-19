package com.neaz.travel.visa.infrastructure.llm;

import com.neaz.travel.visa.application.port.ChatPort;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * Generates chat completions via Spring AI's auto-configured {@link ChatModel},
 * backed by {@code gpt-4o-mini} (or Ollama, under the {@code ollama} profile).
 *
 * <p>{@code modelId} is accepted per {@link ChatPort}'s contract but not used
 * to select the model here — the injected {@link ChatModel} always targets
 * whichever single model is configured via
 * {@code spring.ai.openai.chat.options.model}; this project does not switch
 * models per call. Callers pass {@code modelId} through for audit logging.
 */
@Component
public class ChatAdapter implements ChatPort {

    private final ChatModel chatModel;

    public ChatAdapter(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String generate(String prompt, String modelId) {
        return chatModel.call(prompt);
    }
}