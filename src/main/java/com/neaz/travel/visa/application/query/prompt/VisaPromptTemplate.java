package com.neaz.travel.visa.application.query.prompt;

import com.neaz.travel.visa.domain.model.RetrievedContext;
import com.neaz.travel.visa.domain.model.ScoredChunk;
import com.neaz.travel.visa.domain.model.VisaQuery;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Renders the plain-text prompt sent to {@code ChatPort.generate}, grounding
 * the model in the retrieved source excerpts and enforcing the no-hallucination
 * and citation rules from the project's RAG principles.
 *
 * <p><b>Numbering coupling:</b> excerpts are numbered {@code [1]}, {@code [2]}, ...
 * in {@code context.scoredChunks()} order, and the model is instructed to cite
 * claims using those numbers. {@code CitationBuilder} must assign citation
 * numbers using the exact same order over the exact same {@link RetrievedContext}
 * instance, or the bracket references in the generated answer will point to the
 * wrong source. This is a convention enforced by the caller (passing one
 * post-rerank context to both classes), not by any shared type.
 */
@Component
public class VisaPromptTemplate {

    private static final String SYSTEM_INSTRUCTIONS = """
            You are a visa information assistant. Answer ONLY using the numbered
            source excerpts below. Every factual claim in your answer must be
            followed by the bracket reference number(s) of the excerpt(s) it came
            from, e.g. [1] or [1][3].

            If the excerpts do not contain enough information to answer the
            question, respond with exactly this sentence and nothing else:
            "I couldn't find sufficient official information."

            Never state a visa rule that is not directly supported by the excerpts.
            """;

    public String render(VisaQuery query, RetrievedContext context) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(context, "context must not be null");

        return SYSTEM_INSTRUCTIONS
                + "\nSource excerpts:\n" + renderExcerpts(context.scoredChunks())
                + "\nQuestion: " + query.queryText()
                + "\n\nAnswer:";
    }

    private String renderExcerpts(List<ScoredChunk> scoredChunks) {
        StringBuilder excerpts = new StringBuilder();
        for (int i = 0; i < scoredChunks.size(); i++) {
            excerpts.append('[').append(i + 1).append("] ")
                    .append(scoredChunks.get(i).chunk().getContent())
                    .append('\n');
        }
        return excerpts.toString();
    }
}