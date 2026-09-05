package com.neaz.travel.visa.domain.model;

import java.util.Set;

/**
 * Outcome of the pre-OCR image-quality gate on an uploaded document image.
 * {@code issues} is empty when {@code acceptable} is true, and non-empty
 * otherwise.
 */
public record ImageQualityAssessment(boolean acceptable, Set<ImageQualityIssue> issues) {

    public ImageQualityAssessment {
        issues = Set.copyOf(issues);
        if (acceptable && !issues.isEmpty()) {
            throw new IllegalArgumentException("An acceptable assessment must not carry quality issues");
        }
        if (!acceptable && issues.isEmpty()) {
            throw new IllegalArgumentException("A rejected assessment must carry at least one quality issue");
        }
    }

    public static ImageQualityAssessment accepted() {
        return new ImageQualityAssessment(true, Set.of());
    }

    public static ImageQualityAssessment rejected(Set<ImageQualityIssue> issues) {
        return new ImageQualityAssessment(false, issues);
    }
}
