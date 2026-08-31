package com.neaz.travel.visa.application.command;

import java.util.Objects;
import java.util.UUID;

/**
 * Input to the applicant-profile field-verification use case, built by
 * {@code ApplicantProfileController} from a request body. {@code correctedValue}
 * is whatever the request body's JSON value deserialized to (a {@code String},
 * a {@code Number}, or a {@code Map} for composite fields like an address) —
 * same convention as {@code SetApplicantFieldCommand.value}.
 *
 * @param applicantId    the opaque applicant identity owning the profile
 * @param fieldPath      one of {@code ApplicantProfile}'s {@code PATH_*} constants
 * @param action         which verification transition to apply
 * @param correctedValue the replacement value; required for {@link Action#CORRECT}, must be {@code null} otherwise
 */
public record VerifyApplicantFieldCommand(UUID applicantId, String fieldPath, Action action, Object correctedValue) {

    public VerifyApplicantFieldCommand {
        Objects.requireNonNull(applicantId, "applicantId must not be null");
        if (fieldPath == null || fieldPath.isBlank()) {
            throw new IllegalArgumentException("fieldPath must not be blank");
        }
        Objects.requireNonNull(action, "action must not be null");
        if (action == Action.CORRECT && correctedValue == null) {
            throw new IllegalArgumentException("correctedValue must not be null when action is CORRECT");
        }
        if (action != Action.CORRECT && correctedValue != null) {
            throw new IllegalArgumentException("correctedValue must be null unless action is CORRECT");
        }
    }

    public enum Action {
        CONFIRM,
        CORRECT,
        REJECT
    }
}
