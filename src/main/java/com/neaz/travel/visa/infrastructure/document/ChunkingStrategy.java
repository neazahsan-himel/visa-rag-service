package com.neaz.travel.visa.infrastructure.document;

import com.neaz.travel.visa.application.port.DocumentChunkingPort;
import com.neaz.travel.visa.domain.model.Chunk;
import com.neaz.travel.visa.domain.model.DocumentMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Splits document text into {@link Chunk}s using a sentence-boundary-aware
 * sliding window: sentences are packed into a chunk up to the configured
 * token size, and the trailing sentences of each chunk are carried into the
 * next as overlap so cross-boundary context is preserved.
 *
 * <p>Token counts are approximated as whitespace-separated word counts; this
 * is not a real model tokenizer.
 */
@Component
public class ChunkingStrategy implements DocumentChunkingPort {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final int chunkSize;
    private final int overlapSize;

    public ChunkingStrategy(
            @Value("${rag.chunking.size}") int chunkSize,
            @Value("${rag.chunking.overlap}") int overlapSize) {
        this.chunkSize = chunkSize;
        this.overlapSize = overlapSize;
    }

    @Override
    public List<Chunk> chunk(String text, UUID documentId, DocumentMetadata metadata) {
        List<String> sentences = splitIntoSentences(text);

        List<Chunk> chunks = new ArrayList<>();
        List<String> buffer = new ArrayList<>();
        int bufferTokens = 0;
        int chunkIndex = 0;

        for (String sentence : sentences) {
            int sentenceTokens = tokenCount(sentence);

            if (!buffer.isEmpty() && bufferTokens + sentenceTokens > chunkSize) {
                chunks.add(buildChunk(buffer, documentId, chunkIndex++));
                buffer = carryOverlap(buffer);
                bufferTokens = tokenCount(String.join(" ", buffer));
            }

            buffer.add(sentence);
            bufferTokens += sentenceTokens;
        }

        if (!buffer.isEmpty()) {
            chunks.add(buildChunk(buffer, documentId, chunkIndex));
        }

        return chunks;
    }

    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
        iterator.setText(text);

        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = text.substring(start, end).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }

    /**
     * Selects the trailing sentences of a just-closed chunk whose combined
     * token count fits within {@code overlapSize}, to seed the next chunk.
     */
    private List<String> carryOverlap(List<String> closedChunk) {
        List<String> overlap = new ArrayList<>();
        int tokens = 0;

        for (int i = closedChunk.size() - 1; i >= 0; i--) {
            String sentence = closedChunk.get(i);
            int sentenceTokens = tokenCount(sentence);
            if (!overlap.isEmpty() && tokens + sentenceTokens > overlapSize) {
                break;
            }
            overlap.add(0, sentence);
            tokens += sentenceTokens;
        }
        return overlap;
    }

    private Chunk buildChunk(List<String> sentences, UUID documentId, int chunkIndex) {
        String content = String.join(" ", sentences);
        return new Chunk(
                UUID.randomUUID(),
                documentId,
                chunkIndex,
                content,
                tokenCount(content),
                UUID.randomUUID()
        );
    }

    private int tokenCount(String text) {
        String trimmed = text.trim();
        return trimmed.isEmpty() ? 0 : WHITESPACE.split(trimmed).length;
    }
}