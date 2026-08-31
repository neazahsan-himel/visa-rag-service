package com.neaz.travel.visa.domain.model;

/**
 * The trust state of an {@link ExtractedField}'s value. A field starts
 * {@code UNVERIFIED} and must be moved out of that state by a human before
 * anything downstream (form preparation, consistency checks) may rely on it.
 */
public enum VerificationStatus {

    /** Not yet reviewed by the applicant; the value must not be trusted. */
    UNVERIFIED,

    /** The applicant reviewed the extracted/normalized value and accepted it unchanged. */
    USER_CONFIRMED,

    /** The applicant reviewed the value and replaced it with a corrected one. */
    USER_CORRECTED,

    /** The applicant reviewed the value and rejected it outright (no usable value). */
    REJECTED
}
