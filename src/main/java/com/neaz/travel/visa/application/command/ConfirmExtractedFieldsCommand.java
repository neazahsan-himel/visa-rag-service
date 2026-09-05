package com.neaz.travel.visa.application.command;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Input to the "confirm extracted document fields" use case, built by
 * {@code ApplicantDocumentController} from the applicant's review of a
 * {@code DocumentExtractionResult}. {@code fields} carries only the
 * candidates the applicant chose to keep (unchanged or edited) — a
 * candidate the applicant discards is simply left out, never written.
 * Every accepted value becomes {@code USER_CONFIRMED} with confidence 1.0
 * once a human has reviewed it; the OCR-time confidence and raw/normalized
 * distinction are not carried past this point, the same way a self-declared
 * field has no meaningful "as extracted" value to preserve.
 *
 * @param applicantId the opaque applicant identity owning the profile
 * @param documentId  the {@code ApplicantDocument} these candidates came from
 * @param fields      accepted values, keyed by {@code ApplicantProfile}'s {@code PATH_*} constants
 */
public record ConfirmExtractedFieldsCommand(UUID applicantId, UUID documentId, Map<String, Object> fields) {

    public ConfirmExtractedFieldsCommand {
        Objects.requireNonNull(applicantId, "applicantId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(fields, "fields must not be null");
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("fields must not be empty; confirm at least one field");
        }
        fields = Map.copyOf(fields);
    }
}
