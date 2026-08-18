package com.neaz.travel.visa.domain.model;

import java.util.Objects;

/**
 * Identifies where an ingestible document's raw bytes come from. Currently
 * always a filesystem path (resolved by {@code FileSystemDocumentLoader}
 * against the configured knowledge-base root); the wrapper exists so
 * {@code DocumentParsingPort} does not depend on that assumption directly.
 *
 * @param location the document's location, e.g. a path relative to the
 *                 knowledge-base root
 */
public record DocumentSource(String location) {

    public DocumentSource {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location must not be blank");
        }
    }
}