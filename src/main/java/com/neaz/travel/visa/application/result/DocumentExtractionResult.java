package com.neaz.travel.visa.application.result;

import com.neaz.travel.visa.domain.model.DocumentProcessingStatus;
import com.neaz.travel.visa.domain.model.ExtractedField;
import com.neaz.travel.visa.domain.model.ImageQualityIssue;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Outcome of {@code DocumentIntelligenceService}'s upload-to-candidates
 * pipeline for one {@code ApplicantDocument}. Exactly one of two shapes:
 * {@code QUALITY_REJECTED} (image too degraded to OCR reliably, or no MRZ
 * could be located/parsed — {@code message} carries fixed user-facing text
 * and {@code issues} carries the specific, structured reason(s) so a client
 * can tell the applicant exactly what to fix rather than a generic "try
 * again"), or {@code EXTRACTED} (field candidates awaiting applicant review;
 * values carry {@code VerificationStatus.UNVERIFIED}). {@code fieldCandidates}
 * is keyed by {@code ApplicantProfile.PATH_*} constants, except where MRZ and
 * visual extraction disagree on a field's value: there, the plain path key
 * is absent and the two candidates appear under {@code path + ".conflict.mrz"}
 * and {@code path + ".conflict.visual"} instead, so the applicant can resolve
 * the conflict rather than one source being silently trusted over the other
 * (see {@code DocumentIntelligenceService.mergeWithCrossValidation}).
 */
public record DocumentExtractionResult(
        UUID documentId,
        DocumentProcessingStatus status,
        String message,
        Set<ImageQualityIssue> issues,
        Map<String, ExtractedField<?>> fieldCandidates) {

    public DocumentExtractionResult {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (status != DocumentProcessingStatus.QUALITY_REJECTED && status != DocumentProcessingStatus.EXTRACTED) {
            throw new IllegalArgumentException("status must be QUALITY_REJECTED or EXTRACTED, was " + status);
        }
        if (status == DocumentProcessingStatus.QUALITY_REJECTED && (message == null || message.isBlank())) {
            throw new IllegalArgumentException("message is required when status is QUALITY_REJECTED");
        }
        if (status == DocumentProcessingStatus.QUALITY_REJECTED && issues.isEmpty()) {
            throw new IllegalArgumentException("issues must not be empty when status is QUALITY_REJECTED");
        }
        if (status == DocumentProcessingStatus.EXTRACTED && !issues.isEmpty()) {
            throw new IllegalArgumentException("issues must be empty when status is EXTRACTED");
        }
        issues = Set.copyOf(issues);
        fieldCandidates = Map.copyOf(fieldCandidates);
    }

    public static DocumentExtractionResult qualityRejected(UUID documentId, String message, Set<ImageQualityIssue> issues) {
        return new DocumentExtractionResult(documentId, DocumentProcessingStatus.QUALITY_REJECTED, message, issues, Map.of());
    }

    public static DocumentExtractionResult extracted(UUID documentId, Map<String, ExtractedField<?>> fieldCandidates) {
        return new DocumentExtractionResult(documentId, DocumentProcessingStatus.EXTRACTED, null, Set.of(), fieldCandidates);
    }
}
