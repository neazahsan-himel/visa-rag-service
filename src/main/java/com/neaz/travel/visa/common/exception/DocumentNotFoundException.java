package com.neaz.travel.visa.common.exception;

import java.util.UUID;

/**
 * Thrown when a {@code Document} lookup by its identifier finds no matching record.
 */
public class DocumentNotFoundException extends VisaRagException {

    private final UUID documentId;

    public DocumentNotFoundException(UUID documentId) {
        super("Document not found: " + documentId);
        this.documentId = documentId;
    }

    public UUID getDocumentId() {
        return documentId;
    }
}