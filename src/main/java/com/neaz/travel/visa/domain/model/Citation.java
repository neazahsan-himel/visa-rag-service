package com.neaz.travel.visa.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A structured citation attached to a {@link VisaAnswer}, tracing a stated
 * fact back to the specific document chunk it came from.
 *
 * @param ref         the in-answer reference marker (e.g. "[1]")
 * @param source      human-readable name of the source document
 * @param url         official source URL; may be {@code null}
 * @param lastUpdated the last-updated date of the source document
 * @param chunkId     the {@link Chunk} this citation was derived from
 */
public record Citation(String ref, String source, String url, LocalDate lastUpdated, UUID chunkId) {

    public Citation {
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("ref must not be blank");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        Objects.requireNonNull(lastUpdated, "lastUpdated must not be null");
        Objects.requireNonNull(chunkId, "chunkId must not be null");
    }
}