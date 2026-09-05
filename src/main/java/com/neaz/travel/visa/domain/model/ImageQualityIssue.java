package com.neaz.travel.visa.domain.model;

/**
 * A specific reason an uploaded document image failed the pre-OCR
 * quality gate. An {@link ImageQualityAssessment} may carry more than one.
 */
public enum ImageQualityIssue {

    /** The image is too out-of-focus to OCR reliably (low edge sharpness). */
    BLURRY,

    /** The document is rotated/skewed enough to distort text and MRZ line alignment. */
    SKEWED,

    /** The image is too dark, too bright, or too low-contrast to read reliably. */
    POOR_LIGHTING,

    /** Strong glare or shadow obscures part of the document. */
    GLARE_OR_SHADOW,

    /** The machine-readable zone could not be reliably located or read in the image. */
    MRZ_NOT_READABLE
}
