package com.neaz.travel.visa.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Loads the answer-quality golden set from a JSON file on the test classpath.
 *
 * <p>Fails fast on a malformed set — an empty case list or a duplicated case
 * id — because either would silently skew the aggregated metrics.
 * Per-case validation is enforced by {@link GoldenCase}'s constructor.
 */
public class GoldenSetLoader {

    private final ObjectMapper objectMapper;

    public GoldenSetLoader(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * @param classpathResource resource path, e.g. {@code evaluation/golden-set.json}
     * @return the golden cases, in file order
     * @throws IllegalArgumentException if the resource does not exist
     * @throws IllegalStateException    if the set is empty or contains duplicate ids
     * @throws UncheckedIOException     if the file cannot be read or parsed
     */
    public List<GoldenCase> load(String classpathResource) {
        GoldenSetFile file = read(classpathResource);
        List<GoldenCase> cases = file.cases() == null ? List.of() : List.copyOf(file.cases());

        if (cases.isEmpty()) {
            throw new IllegalStateException("Golden set " + classpathResource + " contains no cases");
        }
        requireUniqueIds(cases, classpathResource);
        return cases;
    }

    private GoldenSetFile read(String classpathResource) {
        try (InputStream in = GoldenSetLoader.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalArgumentException("Golden set not found on classpath: " + classpathResource);
            }
            return objectMapper.readValue(in, GoldenSetFile.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read golden set " + classpathResource, e);
        }
    }

    private static void requireUniqueIds(List<GoldenCase> cases, String classpathResource) {
        Set<String> seen = new HashSet<>();
        for (GoldenCase goldenCase : cases) {
            if (!seen.add(goldenCase.id())) {
                throw new IllegalStateException(
                        "Duplicate case id '" + goldenCase.id() + "' in golden set " + classpathResource);
            }
        }
    }

    /**
     * Top-level shape of the golden-set JSON file. {@code version} and
     * {@code description} are documentation for human reviewers and are not
     * used by the evaluation.
     */
    record GoldenSetFile(int version, String description, List<GoldenCase> cases) {
    }
}
