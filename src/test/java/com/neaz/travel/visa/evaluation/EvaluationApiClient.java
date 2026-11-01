package com.neaz.travel.visa.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neaz.travel.visa.common.model.ApiResponse;
import com.neaz.travel.visa.presentation.dto.VisaQueryRequest;
import com.neaz.travel.visa.presentation.dto.VisaQueryResponse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * Sends golden-case questions to a running service's
 * {@code POST /api/v1/visa/query} endpoint, so the evaluation measures
 * exactly what an API client receives — controllers, mappers, and the
 * {@code GlobalExceptionHandler} low-confidence fallback included.
 */
public class EvaluationApiClient {

    private static final String QUERY_PATH = "/api/v1/visa/query";
    private static final TypeReference<ApiResponse<VisaQueryResponse>> RESPONSE_TYPE = new TypeReference<>() {
    };

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI queryUri;
    private final Duration requestTimeout;

    /**
     * @param httpClient     client used for all requests
     * @param objectMapper   mapper able to read {@code LocalDate} and {@code Instant} fields
     * @param baseUrl        service base URL, e.g. {@code http://localhost:8080}
     * @param requestTimeout per-request timeout; must cover a full retrieval + LLM round trip
     */
    public EvaluationApiClient(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl,
                               Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        this.queryUri = URI.create(baseUrl.replaceAll("/+$", "") + QUERY_PATH);
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
    }

    /**
     * @param goldenCase the case whose question to ask
     * @return the endpoint's response payload
     * @throws IllegalStateException if the endpoint returns a non-2xx status or an unsuccessful envelope
     * @throws UncheckedIOException  if the request fails or the response cannot be parsed
     */
    public VisaQueryResponse ask(GoldenCase goldenCase) {
        VisaQueryRequest body = new VisaQueryRequest(
                goldenCase.question(),
                goldenCase.passportIso(),
                goldenCase.destinationIso(),
                goldenCase.visaTypeCode());

        try {
            HttpRequest request = HttpRequest.newBuilder(queryUri)
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Case " + goldenCase.id() + ": HTTP "
                        + response.statusCode() + " — " + response.body());
            }

            ApiResponse<VisaQueryResponse> envelope = objectMapper.readValue(response.body(), RESPONSE_TYPE);
            if (!envelope.success() || envelope.data() == null) {
                throw new IllegalStateException("Case " + goldenCase.id() + ": unsuccessful response — "
                        + envelope.message());
            }
            return envelope.data();
        } catch (IOException e) {
            throw new UncheckedIOException("Case " + goldenCase.id() + ": request to " + queryUri + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Case " + goldenCase.id() + ": interrupted", e);
        }
    }
}
