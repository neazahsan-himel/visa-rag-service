package com.neaz.travel.visa.common.exception;

import java.util.UUID;

/**
 * Thrown when an {@code ApplicantDocument} lookup by id finds no matching
 * record, or the caller's applicantId does not own the matched document.
 */
public class ApplicantDocumentNotFoundException extends VisaRagException {

    private final UUID documentId;

    public ApplicantDocumentNotFoundException(UUID documentId) {
        super("Applicant document not found: " + documentId);
        this.documentId = documentId;
    }

    public UUID getDocumentId() {
        return documentId;
    }
}
