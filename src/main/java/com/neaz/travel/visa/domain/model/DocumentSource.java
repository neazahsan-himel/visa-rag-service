package com.neaz.travel.visa.domain.model;

import java.util.Objects;

/**
 * Identifies where an ingestible document's raw bytes come from: either a
 * filesystem path relative to the knowledge-base root (resolved by {@code
 * FileSystemDocumentLoader}), or an {@code http(s)://} URL (resolved by
 * {@code UrlDocumentLoader}). {@code DocumentParsingPort}'s implementation
 * dispatches on which one this is; callers do not need to know.
 *
 * @param location the document's location: a knowledge-base-relative path,
 *                 or an {@code http(s)://} URL
 */
public record DocumentSource(String location) {

    public DocumentSource {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location must not be blank");
        }
    }
}