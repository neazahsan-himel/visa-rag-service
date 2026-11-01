package com.neaz.travel.visa.evaluation;

import java.util.List;
import java.util.Objects;

/**
 * Score for a single golden case, produced by {@code CaseScorer} and
 * aggregated by {@code EvaluationReport}.
 *
 * @param caseId              id of the scored {@link GoldenCase}
 * @param category            category of the scored case
 * @param refused             whether the system returned the insufficient-information response
 * @param refusalCorrect      whether {@code refused} matches what the case expects
 * @param firstRelevantRank   1-based position of the first citation whose source is an
 *                            expected source; {@code 0} if none was cited
 * @param factsExpected       number of expected fact groups
 * @param missingFacts        expected fact groups not found in the answer
 * @param forbiddenFactsFound forbidden phrases that appeared in the answer
 * @param confidenceScore     confidence score returned by the system
 * @param error               why the request itself failed (timeout, non-2xx, unparseable
 *                            response); {@code null} when the system answered
 */
public record CaseScore(
        String caseId,
        GoldenCase.Category category,
        boolean refused,
        boolean refusalCorrect,
        int firstRelevantRank,
        int factsExpected,
        List<List<String>> missingFacts,
        List<String> forbiddenFactsFound,
        double confidenceScore,
        String error) {

    public CaseScore {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be blank");
        }
        Objects.requireNonNull(category, "category must not be null");
        if (firstRelevantRank < 0) {
            throw new IllegalArgumentException("firstRelevantRank must not be negative");
        }
        missingFacts = Objects.requireNonNull(missingFacts, "missingFacts must not be null").stream()
                .map(List::copyOf)
                .toList();
        if (factsExpected < 0 || missingFacts.size() > factsExpected) {
            throw new IllegalArgumentException("missingFacts must not exceed factsExpected");
        }
        forbiddenFactsFound = List.copyOf(Objects.requireNonNull(forbiddenFactsFound,
                "forbiddenFactsFound must not be null"));
        if (confidenceScore < 0.0 || confidenceScore > 1.0) {
            throw new IllegalArgumentException("confidenceScore must be within [0.0, 1.0]");
        }
    }

    /**
     * @return {@code true} if the request failed and the system never answered
     */
    public boolean errored() {
        return error != null;
    }

    /**
     * @return {@code true} if an expected source appeared anywhere in the citations
     */
    public boolean sourceHit() {
        return firstRelevantRank > 0;
    }

    /**
     * @return {@code 1 / firstRelevantRank}, or {@code 0.0} if no expected source was cited
     */
    public double reciprocalRank() {
        return sourceHit() ? 1.0 / firstRelevantRank : 0.0;
    }

    /**
     * @return fraction of expected fact groups found in the answer; {@code 1.0}
     *         when the case expects no facts
     */
    public double factRecall() {
        if (factsExpected == 0) {
            return 1.0;
        }
        return (double) (factsExpected - missingFacts.size()) / factsExpected;
    }

    /**
     * @return {@code true} if the answer contained at least one forbidden phrase
     */
    public boolean hallucinated() {
        return !forbiddenFactsFound.isEmpty();
    }

    /**
     * A case passes only if the system answered, the refusal behaviour is
     * correct, nothing forbidden was stated, and — for cases that expect an
     * answer — an expected source was cited and every expected fact was found.
     *
     * @return whether this case passed
     */
    public boolean passed() {
        if (errored() || !refusalCorrect || hallucinated()) {
            return false;
        }
        if (category == GoldenCase.Category.REFUSAL) {
            return true;
        }
        return sourceHit() && missingFacts.isEmpty();
    }
}
