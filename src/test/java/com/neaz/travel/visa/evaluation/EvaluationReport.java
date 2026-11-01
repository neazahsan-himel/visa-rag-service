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
 *
 * <p>The same metrics are also broken down by question {@link Language}, so a
 * retrieval or answer-quality gap for Bengali or mixed-language questions is
 * visible instead of being averaged away by the English majority.
 */
public class EvaluationReport {

    /**
     * Language of a golden case's question, derived from its id prefix — the
     * convention documented in {@code golden-set.json}'s description. Kept in the
     * report rather than in {@link GoldenCase} because only reporting uses it.
     */
    public enum Language {
        /** No language prefix — the default for the original English cases. */
        ENGLISH("English", null),
        /** Id prefix {@code bn-}: question written in Bengali script. */
        BENGALI("Bengali", "bn-"),
        /** Id prefix {@code mixed-}: romanised Bengali or a Bengali–English mix. */
        MIXED("Mixed (romanised Bengali)", "mixed-");

        private final String label;
        private final String idPrefix;

        Language(String label, String idPrefix) {
            this.label = label;
            this.idPrefix = idPrefix;
        }

        /**
         * @return human-readable name used in the report
         */
        public String label() {
            return label;
        }

        /**
         * @param caseId a golden case id
         * @return the language whose prefix the id starts with; {@link #ENGLISH} if none matches
         */
        public static Language fromCaseId(String caseId) {
            Objects.requireNonNull(caseId, "caseId must not be null");
            for (Language language : values()) {
                if (language.idPrefix != null && caseId.startsWith(language.idPrefix)) {
                    return language;
                }
            }
            return ENGLISH;
        }
    }

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
     * @param language the question language to restrict to
     * @return the scores of cases in {@code language}; empty if the set has none
     */
    public List<CaseScore> scoresFor(Language language) {
        Objects.requireNonNull(language, "language must not be null");
        return scores.stream().filter(s -> Language.fromCaseId(s.caseId()) == language).toList();
    }

    /**
     * @return fraction of answer-expecting cases that cited an expected source
     */
    public double sourceHitRate() {
        return fraction(answerCases(scores), CaseScore::sourceHit);
    }

    /**
     * @return mean reciprocal rank of the first expected source, over answer-expecting cases
     */
    public double meanReciprocalRank() {
        return answerCases(scores).stream().mapToDouble(CaseScore::reciprocalRank).average().orElse(0.0);
    }

    /**
     * @return mean fact recall over answer-expecting cases
     */
    public double meanFactRecall() {
        return meanFactRecall(scores);
    }

    /**
     * @return fraction of {@code REFUSAL} cases the system correctly refused
     */
    public double refusalRecall() {
        return fraction(refusalCases(scores), CaseScore::refused);
    }

    /**
     * @return fraction of answer-expecting cases the system wrongly refused
     */
    public double falseRefusalRate() {
        return fraction(answerCases(scores), CaseScore::refused);
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
     *         per-language metrics, a detail row for every failed case, and the
     *         reason for every errored case
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

        appendLanguageSection(md);

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

    /**
     * Rate metrics whose denominator is empty for a language (e.g. no refusal
     * cases) are shown as "n/a" rather than 0.0%, which would read as a failure.
     */
    private void appendLanguageSection(StringBuilder md) {
        md.append("\n## By Language\n\n");
        md.append("| Language | Cases | Pass rate | Source hit rate | Mean fact recall"
                + " | Refusal recall | False refusal rate | Hallucinations | Errors |\n");
        md.append("|---|---|---|---|---|---|---|---|---|\n");
        for (Language language : Language.values()) {
            List<CaseScore> inLanguage = scoresFor(language);
            if (inLanguage.isEmpty()) {
                continue;
            }
            List<CaseScore> answers = answerCases(inLanguage);
            List<CaseScore> refusals = refusalCases(inLanguage);
            md.append("| ").append(language.label())
                    .append(" | ").append(inLanguage.size())
                    .append(" | ").append(percent(fraction(inLanguage, CaseScore::passed)))
                    .append(" | ").append(rateOrNa(answers, CaseScore::sourceHit))
                    .append(" | ").append(answers.isEmpty() ? "n/a" : percent(meanFactRecall(inLanguage)))
                    .append(" | ").append(rateOrNa(refusals, CaseScore::refused))
                    .append(" | ").append(rateOrNa(answers, CaseScore::refused))
                    .append(" | ").append(inLanguage.stream().filter(CaseScore::hallucinated).count())
                    .append(" | ").append(inLanguage.stream().filter(CaseScore::errored).count())
                    .append(" |\n");
        }
    }

    private static double meanFactRecall(List<CaseScore> subset) {
        return answerCases(subset).stream().mapToDouble(CaseScore::factRecall).average().orElse(0.0);
    }

    private static List<CaseScore> answerCases(List<CaseScore> subset) {
        return subset.stream().filter(s -> s.category() != GoldenCase.Category.REFUSAL).toList();
    }

    private static List<CaseScore> refusalCases(List<CaseScore> subset) {
        return subset.stream().filter(s -> s.category() == GoldenCase.Category.REFUSAL).toList();
    }

    private static String rateOrNa(List<CaseScore> subset, Predicate<CaseScore> predicate) {
        return subset.isEmpty() ? "n/a" : percent(fraction(subset, predicate));
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
