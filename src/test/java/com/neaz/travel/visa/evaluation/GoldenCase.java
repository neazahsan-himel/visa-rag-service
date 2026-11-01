package com.neaz.travel.visa.evaluation;

import java.util.List;
import java.util.Objects;

/**
 * One question in the answer-quality golden set, loaded from
 * {@code src/test/resources/evaluation/golden-set.json}.
 *
 * <p>Fact matching is case-insensitive substring. Each inner list of
 * {@code expectedFacts} is a group of acceptable alternatives — the group is
 * satisfied if any one alternative appears in the answer.
 *
 * @param id              stable identifier, used in the evaluation report
 * @param category        what this case is testing
 * @param question        the natural-language question sent to the query endpoint
 * @param passportIso     passport country sent in the request
 * @param destinationIso  destination country sent in the request
 * @param visaTypeCode    visa type sent in the request; {@code null} means all visa types
 * @param expectedSources document titles, any of which counts as a correct citation
 * @param expectedFacts   fact groups the answer must contain
 * @param forbiddenFacts  phrases whose presence indicates a fabricated claim
 */
public record GoldenCase(
        String id,
        Category category,
        String question,
        String passportIso,
        String destinationIso,
        String visaTypeCode,
        List<String> expectedSources,
        List<List<String>> expectedFacts,
        List<String> forbiddenFacts) {

    /**
     * What a golden case is testing.
     */
    public enum Category {
        /** Answer comes from the requested visa type's own document. */
        ANSWERABLE,
        /** Answer comes from a {@code GENERAL} document reached through a specific visa type. */
        GENERAL_FALLBACK,
        /** No source supports an answer; the system must return the insufficient-information response. */
        REFUSAL
    }

    public GoldenCase {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(category, "category must not be null for case " + id);
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank for case " + id);
        }
        Objects.requireNonNull(passportIso, "passportIso must not be null for case " + id);
        Objects.requireNonNull(destinationIso, "destinationIso must not be null for case " + id);

        expectedSources = List.copyOf(Objects.requireNonNull(expectedSources,
                "expectedSources must not be null for case " + id));
        expectedFacts = Objects.requireNonNull(expectedFacts,
                        "expectedFacts must not be null for case " + id).stream()
                .map(group -> requireNonEmptyGroup(group, id))
                .toList();
        forbiddenFacts = List.copyOf(Objects.requireNonNull(forbiddenFacts,
                "forbiddenFacts must not be null for case " + id));

        if (category == Category.REFUSAL) {
            if (!expectedSources.isEmpty() || !expectedFacts.isEmpty()) {
                throw new IllegalArgumentException(
                        "REFUSAL case " + id + " must not declare expected sources or facts");
            }
        } else if (expectedSources.isEmpty() || expectedFacts.isEmpty()) {
            throw new IllegalArgumentException(
                    category + " case " + id + " must declare at least one expected source and fact");
        }
    }

    /**
     * @return {@code true} if the system is expected to refuse this question
     */
    public boolean expectsRefusal() {
        return category == Category.REFUSAL;
    }

    private static List<String> requireNonEmptyGroup(List<String> group, String id) {
        if (group == null || group.isEmpty()) {
            throw new IllegalArgumentException("expectedFacts groups must not be empty for case " + id);
        }
        return List.copyOf(group);
    }
}
