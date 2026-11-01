package com.neaz.travel.visa.evaluation;

import com.neaz.travel.visa.presentation.dto.VisaQueryResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Scores one query response against its {@link GoldenCase} using
 * deterministic string heuristics — no LLM judge — so results are free,
 * fast, and reproducible across runs.
 *
 * <p>All text matching is case-insensitive substring, lower-cased with
 * {@link Locale#ROOT} so it is safe for the Bengali content in the
 * knowledge base.
 */
public class CaseScorer {

    private final String refusalMessage;

    /**
     * @param refusalMessage the system's fixed insufficient-information response;
     *                       its presence anywhere in an answer counts as a refusal,
     *                       which also catches an LLM that declines on its own
     *                       after the confidence gate has passed
     */
    public CaseScorer(String refusalMessage) {
        if (refusalMessage == null || refusalMessage.isBlank()) {
            throw new IllegalArgumentException("refusalMessage must not be blank");
        }
        this.refusalMessage = normalize(refusalMessage);
    }

    /**
     * @param goldenCase the case that was asked
     * @param response   the system's response to that case's question
     * @return the score for this case
     */
    public CaseScore score(GoldenCase goldenCase, VisaQueryResponse response) {
        Objects.requireNonNull(goldenCase, "goldenCase must not be null");
        Objects.requireNonNull(response, "response must not be null");

        String answer = normalize(response.answer());
        boolean refused = answer.contains(refusalMessage);

        return new CaseScore(
                goldenCase.id(),
                goldenCase.category(),
                refused,
                refused == goldenCase.expectsRefusal(),
                firstRelevantRank(goldenCase.expectedSources(), response.citations()),
                goldenCase.expectedFacts().size(),
                missingFacts(goldenCase.expectedFacts(), answer),
                forbiddenFactsFound(goldenCase.forbiddenFacts(), answer),
                response.confidenceScore(),
                null);
    }

    /**
     * Scores a case whose request failed before the system answered: nothing
     * was cited or stated, so every expected fact counts as missing and the
     * case cannot pass.
     *
     * @param goldenCase the case that was asked
     * @param reason     why the request failed
     * @return a failing score carrying {@code reason}
     */
    public CaseScore error(GoldenCase goldenCase, String reason) {
        Objects.requireNonNull(goldenCase, "goldenCase must not be null");
        Objects.requireNonNull(reason, "reason must not be null");

        return new CaseScore(
                goldenCase.id(),
                goldenCase.category(),
                false,
                false,
                0,
                goldenCase.expectedFacts().size(),
                goldenCase.expectedFacts(),
                List.of(),
                0.0,
                reason);
    }

    private static int firstRelevantRank(List<String> expectedSources,
                                         List<VisaQueryResponse.CitationDto> citations) {
        if (citations == null) {
            return 0;
        }
        for (int i = 0; i < citations.size(); i++) {
            if (expectedSources.contains(citations.get(i).source())) {
                return i + 1;
            }
        }
        return 0;
    }

    private static List<List<String>> missingFacts(List<List<String>> expectedFacts, String answer) {
        List<List<String>> missing = new ArrayList<>();
        for (List<String> alternatives : expectedFacts) {
            boolean found = alternatives.stream().anyMatch(alt -> answer.contains(normalize(alt)));
            if (!found) {
                missing.add(alternatives);
            }
        }
        return missing;
    }

    private static List<String> forbiddenFactsFound(List<String> forbiddenFacts, String answer) {
        return forbiddenFacts.stream()
                .filter(phrase -> answer.contains(normalize(phrase)))
                .toList();
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
