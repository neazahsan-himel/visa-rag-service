package com.neaz.travel.visa.application.query.retrieval;

import com.neaz.travel.visa.domain.model.RetrievedContext;
import com.neaz.travel.visa.domain.model.ScoredChunk;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Re-scores {@link HybridRetriever}'s RRF-fused output by boosting chunks
 * with strong keyword overlap against the query, then truncates to a final
 * size suitable for the prompt. Runs entirely in-process — no LLM call, no
 * new port — since RRF fusion score plus keyword overlap is already a
 * reasonable relevance signal without added latency or cost.
 *
 * <p>The keyword-overlap logic here intentionally mirrors
 * {@code ConfidenceScorer.keywordMatchRatio()} in the domain layer; it is not
 * shared via a common utility because doing so would mean modifying that
 * already-shipped class outside this class's own scope.
 */
@Component
public class ReRanker {

    /** Final chunk count kept for the prompt; not specified upstream, chosen as a reasonable context-window budget. */
    private static final int TOP_N = 8;

    private static final Pattern TOKEN_DELIMITER = Pattern.compile("[^a-zA-Z0-9]+");

    public RetrievedContext rerank(RetrievedContext context) {
        Set<String> queryTokens = tokenize(context.queryText());

        var reranked = context.scoredChunks().stream()
                .map(scoredChunk -> rescore(scoredChunk, queryTokens))
                .sorted(Comparator.comparingDouble(ScoredChunk::fusionScore).reversed())
                .limit(TOP_N)
                .toList();

        return new RetrievedContext(context.queryText(), reranked);
    }

    private ScoredChunk rescore(ScoredChunk scoredChunk, Set<String> queryTokens) {
        double keywordOverlapRatio = keywordOverlapRatio(scoredChunk, queryTokens);
        double boostedScore = scoredChunk.fusionScore() * (1.0 + keywordOverlapRatio);
        return new ScoredChunk(scoredChunk.chunk(), boostedScore, scoredChunk.lastUpdated());
    }

    private double keywordOverlapRatio(ScoredChunk scoredChunk, Set<String> queryTokens) {
        if (queryTokens.isEmpty()) {
            return 0.0;
        }
        String content = scoredChunk.chunk().getContent().toLowerCase(Locale.ROOT);
        long matched = queryTokens.stream().filter(content::contains).count();
        return (double) matched / queryTokens.size();
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(TOKEN_DELIMITER.split(text.toLowerCase(Locale.ROOT)))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }
}