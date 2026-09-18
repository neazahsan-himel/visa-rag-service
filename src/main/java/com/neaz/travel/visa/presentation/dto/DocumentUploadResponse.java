package com.neaz.travel.visa.presentation.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response for the applicant-document upload endpoint. Mirrors {@code
 * DocumentExtractionResult}: either {@code status} is {@code
 * QUALITY_REJECTED} (image too degraded to OCR reliably, or no MRZ could be
 * located — {@code message} explains why in one sentence, {@code issues}
 * carries the specific reason code(s) — {@code ImageQualityIssue} enum names
 * such as {@code "BLURRY"}, {@code "POOR_LIGHTING"} — so a client can show
 * the applicant a precise, actionable checklist instead of just the generic
 * message, and {@code fieldCandidates} is empty), or {@code status} is
 * {@code EXTRACTED} and {@code fieldCandidates} holds the MRZ/vision-derived
 * candidates awaiting applicant review, keyed by {@code
 * ApplicantProfile.PATH_*} constants (or {@code path + ".conflict.mrz"} /
 * {@code ".conflict.visual"} where the two sources disagreed); {@code
 * issues} is empty in that case.
 */
public record DocumentUploadResponse(
        UUID documentId,
        String status,
        String message,
        List<String> issues,
        Map<String, FieldCandidateDto> fieldCandidates) {

    /**
     * @param value              the canonical extracted value
     * @param confidence         extraction confidence in {@code [0.0, 1.0]}; see {@code DocumentIntelligenceService}'s per-source confidence Javadoc
     * @param verificationStatus always {@code UNVERIFIED} here; only the profile-field-verify endpoint moves it to {@code USER_CONFIRMED}
     */
    public record FieldCandidateDto(Object value, double confidence, String verificationStatus) {
    }
}
