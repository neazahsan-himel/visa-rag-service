package com.neaz.travel.visa.domain.model;

/**
 * Lifecycle state of an {@link ApplicantDocument} as it moves through the
 * document-intelligence pipeline.
 */
public enum DocumentProcessingStatus {

    /** Received and stored; not yet run through the quality gate or OCR. */
    UPLOADED,

    /** Failed the pre-OCR image-quality gate (blurry, skewed, poorly lit, glare, or MRZ unreadable). */
    QUALITY_REJECTED,

    /** OCR/MRZ extraction produced field candidates awaiting applicant review. */
    EXTRACTED,

    /** The applicant reviewed the candidates and they were written into the applicant's profile. */
    CONFIRMED
}
