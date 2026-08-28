package com.neaz.travel.visa.infrastructure.document;

import com.neaz.travel.visa.common.exception.VisaRagException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches a document's live raw bytes from its official {@code sourceUrl}.
 * Distinct from {@link FileSystemDocumentLoader}, which only reads local
 * {@code knowledge-base/} files and cannot detect upstream changes.
 * <p>
 * Fails closed: any network error or non-2xx response throws, so a fetch
 * failure can never be mistaken for a successful "unchanged" comparison by
 * a caller such as the source-change monitor.
 */
@Component
public class UrlDocumentLoader {

    private static final String USER_AGENT =
            "VisaRagService-SourceMonitor/1.0 (+https://github.com/neazahsan-himel/visa-rag-service)";

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public UrlDocumentLoader(@Value("${source-monitoring.fetch-timeout-seconds:10}") long fetchTimeoutSeconds) {
        this.requestTimeout = Duration.ofSeconds(fetchTimeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(requestTimeout)
                .build();
    }

    /**
     * Loads the raw bytes currently served at the given URL.
     *
     * @param url the document's official source URL
     * @return the response body, as bytes
     * @throws VisaRagException if the URL is malformed, the request times out
     *                          or fails, or the server returns a non-2xx status
     */
    public byte[] load(String url) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(requestTimeout)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            throw new VisaRagException("Malformed source URL: " + url, e);
        }

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new VisaRagException("Failed to fetch source URL: " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VisaRagException("Interrupted while fetching source URL: " + url, e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new VisaRagException("Source URL returned HTTP " + status + ": " + url);
        }
        return response.body();
    }
}
