package com.neaz.travel.visa.application.command;

import java.util.Objects;
import java.util.UUID;

/**
 * Input to the "set an applicant profile field" use case, built by
 * {@code ApplicantProfileController} from a request body. {@code value} is
 * whatever the request body's JSON value deserialized to (a {@code String},
 * a {@code Number}, or a {@code Map} for composite fields like an address) —
 * converted into the field's declared type by {@code ApplicantProfileService}.
 *
 * @param applicantId the opaque applicant identity owning the profile
 * @param fieldPath   one of {@code ApplicantProfile}'s {@code PATH_*} constants
 * @param value       the value to set, as submitted
 */
public record SetApplicantFieldCommand(UUID applicantId, String fieldPath, Object value) {

    public SetApplicantFieldCommand {
        Objects.requireNonNull(applicantId, "applicantId must not be null");
        if (fieldPath == null || fieldPath.isBlank()) {
            throw new IllegalArgumentException("fieldPath must not be blank");
        }
        Objects.requireNonNull(value, "value must not be null");
    }
}
