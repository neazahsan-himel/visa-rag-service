package com.neaz.travel.visa.application.port;

/**
 * Outbound port for generating a chat completion from a prompt. Implemented
 * in {@code infrastructure/llm} by delegating to a configurable chat model
 * (OpenAI or Ollama); the application layer depends only on this contract.
 */
public interface ChatPort {

    /**
     * Generates a completion for the given prompt.
     *
     * @param prompt  the fully-assembled prompt text
     * @param modelId the id of the chat model to use
     * @return the model's raw text response
     */
    String generate(String prompt, String modelId);
}