package com.neaz.travel.visa.application.command;

import com.neaz.travel.visa.domain.model.DocumentType;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Input to the "upload an applicant document" use case, built by
 * {@code ApplicantDocumentController} from a multipart upload.
 *
 * <p>{@code imageBytes} is defensively copied on construction so the
 * command owns its own array; equals/hashCode stay the record default
 * (reference-based for the array field), which is fine since commands are
 * never compared for equality in this codebase.
 *
 * @param applicantId  the opaque applicant identity this document belongs to
 * @param documentType the kind of document being uploaded
 * @param imageBytes   the raw uploaded image bytes (JPEG/PNG)
 */
public record UploadApplicantDocumentCommand(UUID applicantId, DocumentType documentType, byte[] imageBytes) {

    public UploadApplicantDocumentCommand {
        Objects.requireNonNull(applicantId, "applicantId must not be null");
        Objects.requireNonNull(documentType, "documentType must not be null");
        Objects.requireNonNull(imageBytes, "imageBytes must not be null");
        if (imageBytes.length == 0) {
            throw new IllegalArgumentException("imageBytes must not be empty");
        }
        imageBytes = Arrays.copyOf(imageBytes, imageBytes.length);
    }
}
