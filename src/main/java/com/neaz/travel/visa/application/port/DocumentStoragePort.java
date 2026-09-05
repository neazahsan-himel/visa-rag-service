package com.neaz.travel.visa.application.port;

import java.util.UUID;

/**
 * Outbound port for persisting an uploaded applicant document's raw bytes.
 * Implemented in {@code infrastructure/document} against the local
 * filesystem; the application layer depends only on this contract.
 */
public interface DocumentStoragePort {

    /**
     * Stores the raw uploaded bytes for one applicant document.
     *
     * @param applicantId the opaque applicant identity this document belongs to
     * @param documentId  the {@code ApplicantDocument}'s id
     * @param imageBytes  the raw uploaded image bytes (JPEG/PNG)
     * @return a storage reference to persist on {@code ApplicantDocument.storagePath}
     */
    String store(UUID applicantId, UUID documentId, byte[] imageBytes);
}
