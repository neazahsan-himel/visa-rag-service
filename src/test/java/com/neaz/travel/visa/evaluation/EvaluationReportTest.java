package com.neaz.travel.visa.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class EvaluationReportTest {

    @Test
    void metrics_whenMixedResults_computeOverCorrectSubsets() {
        EvaluationReport report = new EvaluationReport(List.of(
                // passed answer case, source at rank 1
                answer("a1", 1, List.of(), List.of(), false, 0.9),
                // failed answer case, source at rank 2, one of two facts missing
                answer("a2", 2, List.of(List.of("x")), List.of(), false, 0.7),
                // wrongly refused answer case
                answer("a3", 0, List.of(List.of("x"), List.of("y")), List.of(), true, 0.3),
                // correctly refused refusal case
                refusal("r1", true, List.of(), 0.2),
                // answered refusal case with a forbidden phrase
                refusal("r2", false, List.of("INR"), 0.65)));

        assertThat(report.passRate()).isEqualTo(2.0 / 5);
        assertThat(report.sourceHitRate()).isEqualTo(2.0 / 3);
        assertThat(report.meanReciprocalRank()).isCloseTo((1.0 + 0.5 + 0.0) / 3, within(1e-9));
        assertThat(report.meanFactRecall()).isCloseTo((1.0 + 0.5 + 0.0) / 3, within(1e-9));
        assertThat(report.refusalRecall()).isEqualTo(0.5);
        assertThat(report.falseRefusalRate()).isEqualTo(1.0 / 3);
        assertThat(report.hallucinationCount()).isEqualTo(1);
        assertThat(report.meanConfidenceOfPassed().getAsDouble()).isCloseTo((0.9 + 0.2) / 2, within(1e-9));
        assertThat(report.meanConfidenceOfFailed().getAsDouble()).isCloseTo((0.7 + 0.3 + 0.65) / 3, within(1e-9));
    }

    @Test
    void meanConfidenceOfFailed_whenAllPassed_isEmpty() {
        EvaluationReport report = new EvaluationReport(List.of(answer("a1", 1, List.of(), List.of(), false, 0.9)));

        assertThat(report.meanConfidenceOfFailed()).isEmpty();
        assertThat(report.toMarkdown()).contains("| Mean confidence — failed | n/a |").contains("None.");
    }

    @Test
    void refusalRecall_whenNoRefusalCases_isZero() {
        EvaluationReport report = new EvaluationReport(List.of(answer("a1", 1, List.of(), List.of(), false, 0.9)));

        assertThat(report.refusalRecall()).isZero();
    }

    @Test
    void toMarkdown_whenCaseFailed_listsItWithMissingFactsAndForbiddenPhrases() {
        EvaluationReport report = new EvaluationReport(List.of(
                answer("tourist-blank-pages", 3, List.of(List.of("two", "2")), List.of("90 days"), false, 0.71)));

        String markdown = report.toMarkdown();

        assertThat(markdown)
                .contains("| Pass rate | 0.0% |")
                .contains("| ANSWERABLE | 0 | 1 |")
                .contains("| tourist-blank-pages | ANSWERABLE | no | 3 | two / 2 | 90 days | 0.710 |");
    }

    @Test
    void report_whenCaseErrored_countsItSeparatelyAndExcludesItFromFailedConfidence() {
        EvaluationReport report = new EvaluationReport(List.of(
                answer("a1", 2, List.of(List.of("x")), List.of(), false, 0.7),
                errored("a2", "Case a2: request timed out")));

        assertThat(report.errorCount()).isEqualTo(1);
        assertThat(report.passRate()).isZero();
        assertThat(report.meanConfidenceOfFailed().getAsDouble()).isCloseTo(0.7, within(1e-9));
        assertThat(report.toMarkdown())
                .contains("| Request errors (no answer received) | 1 |")
                .contains("## Errored Cases")
                .contains("| a2 | ANSWERABLE | Case a2: request timed out |")
                .doesNotContain("| a2 | ANSWERABLE | no |");
    }

    @Test
    void constructor_whenScoresEmpty_throws() {
        assertThatThrownBy(() -> new EvaluationReport(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CaseScore answer(String id, int rank, List<List<String>> missing, List<String> forbidden,
                                    boolean refused, double confidence) {
        return new CaseScore(id, GoldenCase.Category.ANSWERABLE, refused, !refused, rank, 2,
                missing, forbidden, confidence, null);
    }

    private static CaseScore refusal(String id, boolean refused, List<String> forbidden, double confidence) {
        return new CaseScore(id, GoldenCase.Category.REFUSAL, refused, refused, 0, 0,
                List.of(), forbidden, confidence, null);
    }

    private static CaseScore errored(String id, String error) {
        return new CaseScore(id, GoldenCase.Category.ANSWERABLE, false, false, 0, 2,
                List.of(List.of("x"), List.of("y")), List.of(), 0.0, error);
    }
}
