package com.neaz.travel.visa.application.port;

import com.neaz.travel.visa.domain.model.DocumentSource;

/**
 * Outbound port for extracting plain text from a source document. Implemented
 * in {@code infrastructure/document} by delegating to Apache Tika; the
 * application layer depends only on this contract.
 */
public interface DocumentParsingPort {

    /**
     * Extracts plain text from the document at the given source.
     *
     * @param source identifies where to load the document's raw bytes from
     * @return the document's extracted plain text
     */
    String parse(DocumentSource source);
}