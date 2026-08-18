package com.neaz.travel.visa.infrastructure.embedding;

import com.neaz.travel.visa.application.port.EmbeddingPort;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

/**
 * Embeds text via Spring AI's auto-configured {@link EmbeddingModel}, backed
 * by {@code text-embedding-3-small} (or Ollama, under the {@code ollama}
 * profile).
 */
@Component
public class EmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel embeddingModel;

    public EmbeddingAdapter(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }
}