package com.neaz.travel.visa.application.port;

import com.neaz.travel.visa.domain.model.ImageQualityAssessment;

/**
 * Outbound port for the pre-OCR image-quality gate. Implemented in
 * {@code infrastructure/document/quality} using plain-Java image analysis;
 * the application layer depends only on this contract.
 */
public interface ImageQualityPort {

    /**
     * Assesses whether an uploaded document image is clear enough to OCR
     * reliably (sharpness, lighting, glare/shadow, skew, MRZ readability).
     *
     * @param imageBytes the raw uploaded image bytes (JPEG/PNG)
     * @return the quality outcome; rejected assessments carry the specific issues found
     */
    ImageQualityAssessment assess(byte[] imageBytes);
}
