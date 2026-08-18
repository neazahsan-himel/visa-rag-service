package com.neaz.travel.visa.application.command;

import com.neaz.travel.visa.domain.model.DocumentMetadata;

import java.util.Objects;

/**
 * Input to the document ingestion use case, built by
 * {@code IngestionController} from a {@code POST /api/v1/ingestion/documents}
 * request.
 *
 * @param path     the document's location, relative to the configured
 *                 knowledge-base root
 * @param title    a human-readable title for the document
 * @param metadata the document's country/visa-type/passport/source metadata
 */
public record IngestDocumentCommand(String path, String title, DocumentMetadata metadata) {

    public IngestDocumentCommand {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        Objects.requireNonNull(metadata, "metadata must not be null");
    }
}