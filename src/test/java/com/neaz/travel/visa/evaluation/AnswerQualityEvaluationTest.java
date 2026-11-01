package com.neaz.travel.visa.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Runs the golden set against a live, fully-ingested service and enforces a
 * quality baseline.
 *
 * <p>Requires the Docker stack running, the {@code knowledge-base/India/}
 * documents ingested, and a real LLM provider configured. Enabled only when
 * {@code EVAL_BASE_URL} is set, so a plain {@code mvn test} skips it:
 *
 * <pre>
 * EVAL_BASE_URL=http://localhost:8081 ./mvnw test -Dtest=AnswerQualityEvaluationTest
 * </pre>
 *
 * <p>{@code EVAL_REQUEST_TIMEOUT_SECONDS} overrides the per-request timeout
 * (default 120s) — raise it for slow providers such as a CPU-only Ollama.
 *
 * <p>A failed request is recorded as an errored case and the run continues,
 * so one timeout never discards the other cases' results. The Markdown report
 * is written to {@code target/evaluation/report.md} before the baseline is
 * checked, so it is available even when the run fails.
 *
 * <p>The baseline thresholds below are initial values chosen before the first
 * live run; calibrate them against the first report, then only ever raise them.
 */
@Tag("evaluation")
@EnabledIfEnvironmentVariable(named = "EVAL_BASE_URL", matches = ".+")
class AnswerQualityEvaluationTest {

    private static final String GOLDEN_SET = "evaluation/golden-set.json";
    private static final Path REPORT_PATH = Path.of("target", "evaluation", "report.md");
    private static final String REFUSAL_MESSAGE = "I couldn't find sufficient official information.";
    private static final long DEFAULT_REQUEST_TIMEOUT_SECONDS = 120;

    private static final double MIN_SOURCE_HIT_RATE = 0.80;
    private static final double MIN_MEAN_FACT_RECALL = 0.70;
    private static final double MIN_REFUSAL_RECALL = 0.80;
    private static final double MAX_FALSE_REFUSAL_RATE = 0.15;
    private static final long MAX_HALLUCINATIONS = 0;
    private static final long MAX_REQUEST_ERRORS = 0;

    @Test
    void goldenSet_againstLiveService_meetsQualityBaseline() throws IOException {
        ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        List<GoldenCase> cases = new GoldenSetLoader(objectMapper).load(GOLDEN_SET);
        EvaluationApiClient client = new EvaluationApiClient(
                HttpClient.newHttpClient(), objectMapper, System.getenv("EVAL_BASE_URL"), requestTimeout());
        CaseScorer scorer = new CaseScorer(REFUSAL_MESSAGE);

        List<CaseScore> scores = new ArrayList<>();
        for (GoldenCase goldenCase : cases) {
            try {
                scores.add(scorer.score(goldenCase, client.ask(goldenCase)));
            } catch (RuntimeException e) {
                scores.add(scorer.error(goldenCase, describe(e)));
            }
        }

        EvaluationReport report = new EvaluationReport(scores);
        Files.createDirectories(REPORT_PATH.getParent());
        Files.writeString(REPORT_PATH, report.toMarkdown(), StandardCharsets.UTF_8);

        assertSoftly(softly -> {
            softly.assertThat(report.sourceHitRate()).as("source hit rate").isGreaterThanOrEqualTo(MIN_SOURCE_HIT_RATE);
            softly.assertThat(report.meanFactRecall()).as("mean fact recall").isGreaterThanOrEqualTo(MIN_MEAN_FACT_RECALL);
            softly.assertThat(report.refusalRecall()).as("refusal recall").isGreaterThanOrEqualTo(MIN_REFUSAL_RECALL);
            softly.assertThat(report.falseRefusalRate()).as("false refusal rate").isLessThanOrEqualTo(MAX_FALSE_REFUSAL_RATE);
            softly.assertThat(report.hallucinationCount()).as("hallucinations").isLessThanOrEqualTo(MAX_HALLUCINATIONS);
            softly.assertThat(report.errorCount()).as("request errors").isLessThanOrEqualTo(MAX_REQUEST_ERRORS);
        });
    }

    private static Duration requestTimeout() {
        String configured = System.getenv("EVAL_REQUEST_TIMEOUT_SECONDS");
        long seconds = configured == null || configured.isBlank()
                ? DEFAULT_REQUEST_TIMEOUT_SECONDS
                : Long.parseLong(configured.trim());
        return Duration.ofSeconds(seconds);
    }

    private static String describe(RuntimeException e) {
        Throwable cause = e.getCause();
        return cause == null ? e.getMessage() : e.getMessage() + " (" + cause + ")";
    }
}
