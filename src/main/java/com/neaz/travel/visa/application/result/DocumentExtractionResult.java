package com.neaz.travel.visa.application.result;

import com.neaz.travel.visa.domain.model.DocumentProcessingStatus;
import com.neaz.travel.visa.domain.model.ExtractedField;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Outcome of {@code DocumentIntelligenceService}'s upload-to-candidates
 * pipeline for one {@code ApplicantDocument}. Exactly one of two shapes:
 * {@code QUALITY_REJECTED} (image too degraded to OCR reliably, or no MRZ
 * could be located/parsed — {@code message} carries the fixed user-facing
 * text), or {@code EXTRACTED} (field candidates awaiting applicant review;
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
        fieldCandidates = Map.copyOf(fieldCandidates);
    }

    public static DocumentExtractionResult qualityRejected(UUID documentId, String message) {
        return new DocumentExtractionResult(documentId, DocumentProcessingStatus.QUALITY_REJECTED, message, Map.of());
    }

    public static DocumentExtractionResult extracted(UUID documentId, Map<String, ExtractedField<?>> fieldCandidates) {
        return new DocumentExtractionResult(documentId, DocumentProcessingStatus.EXTRACTED, null, fieldCandidates);
    }
}
