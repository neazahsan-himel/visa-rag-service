package com.neaz.travel.visa.application.query.prompt;

import com.neaz.travel.visa.domain.model.Chunk;
import com.neaz.travel.visa.domain.model.MetadataFilter;
import com.neaz.travel.visa.domain.model.RetrievedContext;
import com.neaz.travel.visa.domain.model.ScoredChunk;
import com.neaz.travel.visa.domain.model.VisaQuery;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link VisaPromptTemplate}'s structural contract: excerpts are
 * numbered in {@code scoredChunks()} list order (the numbering
 * {@code CitationBuilder} must mirror), the fixed no-hallucination fallback
 * sentence and citation instruction are present verbatim, and the prompt
 * ends with the expected answer cue.
 */
class VisaPromptTemplateTest {

    private static final MetadataFilter FILTER = new MetadataFilter("BD", "IN", "TOURIST");
    private static final String QUERY_TEXT = "What documents do I need for a tourist visa?";

    private final VisaPromptTemplate promptTemplate = new VisaPromptTemplate();

    @Test
    void render_numbersExcerptsSequentiallyInScoredChunksListOrder() {
        RetrievedContext context = contextWith(
                "First excerpt content.",
                "Second excerpt content.",
                "Third excerpt content.");

        String prompt = promptTemplate.render(new VisaQuery(QUERY_TEXT, FILTER), context);

        assertTrue(prompt.contains(
                "[1] First excerpt content.\n[2] Second excerpt content.\n[3] Third excerpt content.\n"));
    }

    @Test
    void render_includesTheQuestionText() {
        RetrievedContext context = contextWith("Some excerpt content.");

        String prompt = promptTemplate.render(new VisaQuery(QUERY_TEXT, FILTER), context);

        assertTrue(prompt.contains("\nQuestion: " + QUERY_TEXT));
    }

    @Test
    void render_endsWithTheAnswerCue() {
        RetrievedContext context = contextWith("Some excerpt content.");

        String prompt = promptTemplate.render(new VisaQuery(QUERY_TEXT, FILTER), context);

        assertTrue(prompt.endsWith("\n\nAnswer:"));
    }

    @Test
    void render_withEmptyScoredChunks_rendersEmptyExcerptsSectionWithoutError() {
        RetrievedContext context = new RetrievedContext(QUERY_TEXT, List.of());

        String prompt = promptTemplate.render(new VisaQuery(QUERY_TEXT, FILTER), context);

        assertTrue(prompt.contains("\nSource excerpts:\n\nQuestion: " + QUERY_TEXT));
    }

    @Test
    void render_includesCitationInstructionAndNoHallucinationFallbackSentenceVerbatim() {
        RetrievedContext context = contextWith("Some excerpt content.");

        String prompt = promptTemplate.render(new VisaQuery(QUERY_TEXT, FILTER), context);

        assertTrue(prompt.contains("bracket reference number(s)"));
        assertTrue(prompt.contains("\"I couldn't find sufficient official information.\""));
    }

    @Test
    void render_withNullQuery_throwsNullPointerException() {
        RetrievedContext context = contextWith("Some excerpt content.");

        assertThrows(NullPointerException.class, () -> promptTemplate.render(null, context));
    }

    @Test
    void render_withNullContext_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> promptTemplate.render(new VisaQuery(QUERY_TEXT, FILTER), null));
    }

    private RetrievedContext contextWith(String... chunkContents) {
        List<ScoredChunk> scoredChunks = List.of(chunkContents).stream()
                .map(this::scoredChunk)
                .toList();
        return new RetrievedContext(QUERY_TEXT, scoredChunks);
    }

    private ScoredChunk scoredChunk(String content) {
        Chunk chunk = new Chunk(UUID.randomUUID(), UUID.randomUUID(), 0, content, 20, UUID.randomUUID());
        return new ScoredChunk(chunk, 0.05, LocalDate.now());
    }
}
