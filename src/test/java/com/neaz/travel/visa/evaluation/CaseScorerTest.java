package com.neaz.travel.visa.evaluation;

import com.neaz.travel.visa.presentation.dto.VisaQueryResponse;
import com.neaz.travel.visa.presentation.dto.VisaQueryResponse.CitationDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CaseScorerTest {

    private static final String REFUSAL = "I couldn't find sufficient official information.";
    private static final String TOURIST_DOC = "India Tourist Visa - Required Documents (HCI Dhaka)";
    private static final String FEE_DOC = "India Visa - Fee Policy (All Visa Types)";

    private final CaseScorer scorer = new CaseScorer(REFUSAL);

    @Test
    void score_whenAnswerHasAllFactsAndExpectedSourceFirst_passes() {
        GoldenCase goldenCase = answerable(List.of(List.of("six months", "6 months")), List.of());
        VisaQueryResponse response = response("Your passport must be valid for 6 Months.", 0.8, TOURIST_DOC);

        CaseScore score = scorer.score(goldenCase, response);

        assertThat(score.passed()).isTrue();
        assertThat(score.firstRelevantRank()).isEqualTo(1);
        assertThat(score.reciprocalRank()).isEqualTo(1.0);
        assertThat(score.factRecall()).isEqualTo(1.0);
        assertThat(score.refused()).isFalse();
    }

    @Test
    void score_whenExpectedSourceCitedThird_returnsRankThreeAndReciprocalOneThird() {
        GoldenCase goldenCase = answerable(List.of(List.of("six months")), List.of());
        VisaQueryResponse response = response("six months", 0.8, FEE_DOC, FEE_DOC, TOURIST_DOC);

        CaseScore score = scorer.score(goldenCase, response);

        assertThat(score.firstRelevantRank()).isEqualTo(3);
        assertThat(score.reciprocalRank()).isEqualTo(1.0 / 3);
        assertThat(score.passed()).isTrue();
    }

    @Test
    void score_whenExpectedSourceNotCited_failsWithRankZero() {
        GoldenCase goldenCase = answerable(List.of(List.of("six months")), List.of());
        VisaQueryResponse response = response("six months", 0.8, FEE_DOC);

        CaseScore score = scorer.score(goldenCase, response);

        assertThat(score.sourceHit()).isFalse();
        assertThat(score.reciprocalRank()).isZero();
        assertThat(score.passed()).isFalse();
    }

    @Test
    void score_whenOneOfTwoFactGroupsMissing_reportsHalfRecallAndMissingGroup() {
        GoldenCase goldenCase = answerable(
                List.of(List.of("six months"), List.of("two blank pages", "2 blank pages")), List.of());
        VisaQueryResponse response = response("Valid for six months.", 0.8, TOURIST_DOC);

        CaseScore score = scorer.score(goldenCase, response);

        assertThat(score.factRecall()).isEqualTo(0.5);
        assertThat(score.missingFacts()).containsExactly(List.of("two blank pages", "2 blank pages"));
        assertThat(score.passed()).isFalse();
    }

    @Test
    void score_whenForbiddenPhrasePresent_flagsHallucinationAndFails() {
        GoldenCase goldenCase = answerable(List.of(List.of("180 days")), List.of("90 days"));
        VisaQueryResponse response = response("Granted for 180 days, max stay 90 days.", 0.8, TOURIST_DOC);

        CaseScore score = scorer.score(goldenCase, response);

        assertThat(score.hallucinated()).isTrue();
        assertThat(score.forbiddenFactsFound()).containsExactly("90 days");
        assertThat(score.passed()).isFalse();
    }

    @Test
    void score_whenRefusalCaseIsRefused_passes() {
        GoldenCase goldenCase = refusal();
        VisaQueryResponse response = response(REFUSAL, 0.2);

        CaseScore score = scorer.score(goldenCase, response);

        assertThat(score.refused()).isTrue();
        assertThat(score.refusalCorrect()).isTrue();
        assertThat(score.factRecall()).isEqualTo(1.0);
        assertThat(score.passed()).isTrue();
    }

    @Test
    void score_whenRefusalCaseIsAnswered_fails() {
        GoldenCase goldenCase = refusal();
        VisaQueryResponse response = response("The best time is October.", 0.7, TOURIST_DOC);

        CaseScore score = scorer.score(goldenCase, response);

        assertThat(score.refused()).isFalse();
        assertThat(score.refusalCorrect()).isFalse();
        assertThat(score.passed()).isFalse();
    }

    @Test
    void score_whenAnswerableCaseIsRefused_failsAsFalseRefusal() {
        GoldenCase goldenCase = answerable(List.of(List.of("six months")), List.of());
        VisaQueryResponse response = response(REFUSAL, 0.3);

        CaseScore score = scorer.score(goldenCase, response);

        assertThat(score.refused()).isTrue();
        assertThat(score.refusalCorrect()).isFalse();
        assertThat(score.passed()).isFalse();
    }

    @Test
    void score_whenLlmRefusesInsideLongerAnswer_countsAsRefusal() {
        GoldenCase goldenCase = refusal();
        VisaQueryResponse response = response("Sorry. " + REFUSAL.toUpperCase() + " Please check IVAC.", 0.7, TOURIST_DOC);

        CaseScore score = scorer.score(goldenCase, response);

        assertThat(score.refused()).isTrue();
    }

    @Test
    void error_whenRequestFailed_failsWithEveryFactMissingAndReasonKept() {
        GoldenCase goldenCase = answerable(List.of(List.of("bank statement"), List.of("photo")), List.of());

        CaseScore score = scorer.error(goldenCase, "request timed out");

        assertThat(score.errored()).isTrue();
        assertThat(score.error()).isEqualTo("request timed out");
        assertThat(score.passed()).isFalse();
        assertThat(score.sourceHit()).isFalse();
        assertThat(score.factRecall()).isZero();
        assertThat(score.refused()).isFalse();
    }

    @Test
    void error_whenRefusalCaseRequestFailed_fails() {
        CaseScore score = scorer.error(refusal(), "HTTP 500");

        assertThat(score.passed()).isFalse();
        assertThat(score.refused()).isFalse();
    }

    private static GoldenCase answerable(List<List<String>> facts, List<String> forbidden) {
        return new GoldenCase("case-1", GoldenCase.Category.ANSWERABLE, "question?", "BD", "IN", "TOURIST",
                List.of(TOURIST_DOC), facts, forbidden);
    }

    private static GoldenCase refusal() {
        return new GoldenCase("refusal-1", GoldenCase.Category.REFUSAL, "question?", "BD", "IN", "TOURIST",
                List.of(), List.of(), List.of());
    }

    private static VisaQueryResponse response(String answer, double confidence, String... citationSources) {
        List<CitationDto> citations = Arrays.stream(citationSources)
                .map(source -> new CitationDto("[1]", source, null, LocalDate.of(2024, 3, 7), UUID.randomUUID()))
                .toList();
        return new VisaQueryResponse(answer, citations, confidence);
    }
}
