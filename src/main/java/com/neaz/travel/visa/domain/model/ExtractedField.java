package com.neaz.travel.visa.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A single applicant-profile field value, carrying where it came from and
 * whether it can be trusted yet. {@code extractedValue} is what was read
 * verbatim (from OCR, or as typed by the applicant); {@code normalizedValue}
 * is the canonicalized form used everywhere else. The two are never
 * collapsed into one value so a later correction can be compared against
 * what was originally extracted.
 *
 * @param extractedValue     the raw value as read, before normalization
 * @param normalizedValue    the canonical value; what downstream code reads once verified
 * @param confidence         extraction confidence in {@code [0.0, 1.0]}; {@code 1.0} for self-declared values
 * @param sourceDocumentId   the {@code ApplicantDocument} this was extracted from; {@code null} for self-declared values
 * @param verificationStatus whether the applicant has reviewed this value yet
 */
public record ExtractedField<T>(
        T extractedValue,
        T normalizedValue,
        double confidence,
        UUID sourceDocumentId,
        VerificationStatus verificationStatus) {

    public ExtractedField {
        Objects.requireNonNull(extractedValue, "extractedValue must not be null");
        Objects.requireNonNull(normalizedValue, "normalizedValue must not be null");
        Objects.requireNonNull(verificationStatus, "verificationStatus must not be null");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be within [0.0, 1.0], was " + confidence);
        }
    }

    /**
     * A value the applicant typed directly, with no source document. Treated
     * as already confirmed — there is nothing to verify a person's own
     * unmediated input against.
     */
    public static <T> ExtractedField<T> selfDeclared(T value) {
        return new ExtractedField<>(value, value, 1.0, null, VerificationStatus.USER_CONFIRMED);
    }
}
