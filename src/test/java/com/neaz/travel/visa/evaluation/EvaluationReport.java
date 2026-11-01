package com.neaz.travel.visa.evaluation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Aggregates per-case {@link CaseScore}s into run-level metrics and renders
 * them as a Markdown report.
 *
 * <p>Retrieval and fact metrics are computed over cases that expect an answer
 * ({@code ANSWERABLE} and {@code GENERAL_FALLBACK}) only — refusal cases have
 * no expected source or facts and would otherwise inflate or dilute them.
 */
public class EvaluationReport {

    private final List<CaseScore> scores;

    public EvaluationReport(List<CaseScore> scores) {
        this.scores = List.copyOf(Objects.requireNonNull(scores, "scores must not be null"));
        if (this.scores.isEmpty()) {
            throw new IllegalArgumentException("scores must not be empty");
        }
    }

    /**
     * @return fraction of all cases that passed
     */
    public double passRate() {
        return fraction(scores, CaseScore::passed);
    }

    /**
     * @return fraction of answer-expecting cases that cited an expected source
     */
    public double sourceHitRate() {
        return fraction(answerCases(), CaseScore::sourceHit);
    }

    /**
     * @return mean reciprocal rank of the first expected source, over answer-expecting cases
     */
    public double meanReciprocalRank() {
        return answerCases().stream().mapToDouble(CaseScore::reciprocalRank).average().orElse(0.0);
    }

    /**
     * @return mean fact recall over answer-expecting cases
     */
    public double meanFactRecall() {
        return answerCases().stream().mapToDouble(CaseScore::factRecall).average().orElse(0.0);
    }

    /**
     * @return fraction of {@code REFUSAL} cases the system correctly refused
     */
    public double refusalRecall() {
        return fraction(refusalCases(), CaseScore::refused);
    }

    /**
     * @return fraction of answer-expecting cases the system wrongly refused
     */
    public double falseRefusalRate() {
        return fraction(answerCases(), CaseScore::refused);
    }

    /**
     * @return number of cases whose answer contained a forbidden phrase
     */
    public long hallucinationCount() {
        return scores.stream().filter(CaseScore::hallucinated).count();
    }

    /**
     * @return number of cases whose request failed before the system answered
     */
    public long errorCount() {
        return scores.stream().filter(CaseScore::errored).count();
    }

    /**
     * @return mean confidence of passed cases; empty if none passed
     */
    public OptionalDouble meanConfidenceOfPassed() {
        return scores.stream().filter(CaseScore::passed).mapToDouble(CaseScore::confidenceScore).average();
    }

    /**
     * @return mean confidence of failed cases the system answered; empty if
     *         there are none. Errored cases are excluded — they carry no
     *         confidence from the system.
     */
    public OptionalDouble meanConfidenceOfFailed() {
        return scores.stream()
                .filter(s -> !s.passed() && !s.errored())
                .mapToDouble(CaseScore::confidenceScore)
                .average();
    }

    /**
     * @return the report as Markdown: a summary table, per-category pass counts,
     *         a detail row for every failed case, and the reason for every
     *         errored case
     */
    public String toMarkdown() {
        StringBuilder md = new StringBuilder();
        md.append("# Answer-Quality Evaluation Report\n\n");
        md.append("Cases: ").append(scores.size()).append("\n\n");

        md.append("## Summary\n\n");
        md.append("| Metric | Value |\n|---|---|\n");
        row(md, "Pass rate", percent(passRate()));
        row(md, "Source hit rate (answer cases)", percent(sourceHitRate()));
        row(md, "Mean reciprocal rank (answer cases)", decimal(meanReciprocalRank()));
        row(md, "Mean fact recall (answer cases)", percent(meanFactRecall()));
        row(md, "Refusal recall (refusal cases)", percent(refusalRecall()));
        row(md, "False refusal rate (answer cases)", percent(falseRefusalRate()));
        row(md, "Hallucinations (forbidden phrase found)", String.valueOf(hallucinationCount()));
        row(md, "Request errors (no answer received)", String.valueOf(errorCount()));
        row(md, "Mean confidence — passed", optionalDecimal(meanConfidenceOfPassed()));
        row(md, "Mean confidence — failed", optionalDecimal(meanConfidenceOfFailed()));

        md.append("\n## By Category\n\n");
        md.append("| Category | Passed | Total |\n|---|---|---|\n");
        for (GoldenCase.Category category : GoldenCase.Category.values()) {
            List<CaseScore> inCategory = scores.stream().filter(s -> s.category() == category).toList();
            if (!inCategory.isEmpty()) {
                long passed = inCategory.stream().filter(CaseScore::passed).count();
                md.append("| ").append(category).append(" | ").append(passed)
                        .append(" | ").append(inCategory.size()).append(" |\n");
            }
        }

        List<CaseScore> failed = scores.stream().filter(s -> !s.passed() && !s.errored()).toList();
        md.append("\n## Failed Cases\n\n");
        if (failed.isEmpty()) {
            md.append("None.\n");
        } else {
            md.append("| Case | Category | Refused | Source rank | Missing facts | Forbidden found | Confidence |\n");
            md.append("|---|---|---|---|---|---|---|\n");
            for (CaseScore score : failed) {
                md.append("| ").append(score.caseId())
                        .append(" | ").append(score.category())
                        .append(" | ").append(score.refused() ? "yes" : "no")
                        .append(" | ").append(score.sourceHit() ? String.valueOf(score.firstRelevantRank()) : "—")
                        .append(" | ").append(describeMissing(score.missingFacts()))
                        .append(" | ").append(score.forbiddenFactsFound().isEmpty()
                                ? "—" : String.join(", ", score.forbiddenFactsFound()))
                        .append(" | ").append(decimal(score.confidenceScore()))
                        .append(" |\n");
            }
        }

        List<CaseScore> errored = scores.stream().filter(CaseScore::errored).toList();
        if (!errored.isEmpty()) {
            md.append("\n## Errored Cases\n\n");
            md.append("| Case | Category | Error |\n|---|---|---|\n");
            for (CaseScore score : errored) {
                md.append("| ").append(score.caseId())
                        .append(" | ").append(score.category())
                        .append(" | ").append(score.error().replace("|", "\\|").replace("\n", " "))
                        .append(" |\n");
            }
        }
        return md.toString();
    }

    private List<CaseScore> answerCases() {
        return scores.stream().filter(s -> s.category() != GoldenCase.Category.REFUSAL).toList();
    }

    private List<CaseScore> refusalCases() {
        return scores.stream().filter(s -> s.category() == GoldenCase.Category.REFUSAL).toList();
    }

    private static double fraction(List<CaseScore> subset, Predicate<CaseScore> predicate) {
        if (subset.isEmpty()) {
            return 0.0;
        }
        return (double) subset.stream().filter(predicate).count() / subset.size();
    }

    private static String describeMissing(List<List<String>> missingFacts) {
        if (missingFacts.isEmpty()) {
            return "—";
        }
        return missingFacts.stream()
                .map(group -> String.join(" / ", group))
                .collect(Collectors.joining("; "));
    }

    private static void row(StringBuilder md, String metric, String value) {
        md.append("| ").append(metric).append(" | ").append(value).append(" |\n");
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String optionalDecimal(OptionalDouble value) {
        return value.isPresent() ? decimal(value.getAsDouble()) : "n/a";
    }
}
