package com.neaz.travel.visa.application.port;

/**
 * Outbound port for turning text into a vector embedding. Implemented in
 * {@code infrastructure/embedding} by delegating to a concrete embedding
 * model; the application layer depends only on this contract.
 */
public interface EmbeddingPort {

    /**
     * Embeds a single piece of text.
     *
     * @param text the text to embed (e.g. one document chunk, or a query)
     * @return the embedding vector; dimensionality is model-specific
     *         (1536 for {@code text-embedding-3-small})
     */
    float[] embed(String text);
}