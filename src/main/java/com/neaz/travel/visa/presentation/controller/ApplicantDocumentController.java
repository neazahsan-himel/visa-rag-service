package com.neaz.travel.visa.presentation.controller;

import com.neaz.travel.visa.application.command.ConfirmExtractedFieldsCommand;
import com.neaz.travel.visa.application.command.UploadApplicantDocumentCommand;
import com.neaz.travel.visa.application.document.DocumentIntelligenceService;
import com.neaz.travel.visa.application.document.ExtractedFieldConfirmationService;
import com.neaz.travel.visa.application.port.VisionExtractionPort;
import com.neaz.travel.visa.application.result.ApplicantProfileResult;
import com.neaz.travel.visa.application.result.DocumentExtractionResult;
import com.neaz.travel.visa.common.model.ApiResponse;
import com.neaz.travel.visa.domain.model.DocumentType;
import com.neaz.travel.visa.domain.model.VisualPassportData;
import com.neaz.travel.visa.presentation.dto.ApplicantProfileResponse;
import com.neaz.travel.visa.presentation.dto.ConfirmExtractedFieldsRequest;
import com.neaz.travel.visa.presentation.dto.DocumentUploadResponse;
import com.neaz.travel.visa.presentation.dto.VisionPreviewResponse;
import com.neaz.travel.visa.presentation.mapper.ApplicantProfileMapper;
import com.neaz.travel.visa.presentation.mapper.DocumentUploadMapper;
import com.neaz.travel.visa.presentation.mapper.VisionPreviewMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * Applicant-document upload API: the production passport-upload-to-candidates
 * flow ({@link #uploadPassport}) followed by applicant review ({@link
 * #confirmExtractedFields}), plus a diagnostic endpoint for exercising
 * {@link VisionExtractionPort} alone ({@link #previewVisionExtraction}) —
 * bypasses the image-quality gate, OCR, MRZ parsing, and persistence
 * entirely, and does not associate its result with any stored document.
 */
@RestController
@RequestMapping("/api/v1/applicants")
public class ApplicantDocumentController {

    private final VisionExtractionPort visionExtractionPort;
    private final VisionPreviewMapper visionPreviewMapper;
    private final DocumentIntelligenceService documentIntelligenceService;
    private final ExtractedFieldConfirmationService extractedFieldConfirmationService;
    private final DocumentUploadMapper documentUploadMapper;
    private final ApplicantProfileMapper applicantProfileMapper;

    public ApplicantDocumentController(VisionExtractionPort visionExtractionPort,
                                        VisionPreviewMapper visionPreviewMapper,
                                        DocumentIntelligenceService documentIntelligenceService,
                                        ExtractedFieldConfirmationService extractedFieldConfirmationService,
                                        DocumentUploadMapper documentUploadMapper,
                                        ApplicantProfileMapper applicantProfileMapper) {
        this.visionExtractionPort = visionExtractionPort;
        this.visionPreviewMapper = visionPreviewMapper;
        this.documentIntelligenceService = documentIntelligenceService;
        this.extractedFieldConfirmationService = extractedFieldConfirmationService;
        this.documentUploadMapper = documentUploadMapper;
        this.applicantProfileMapper = applicantProfileMapper;
    }

    @PostMapping("/{applicantId}/documents/passport")
    public ApiResponse<DocumentUploadResponse> uploadPassport(
            @PathVariable UUID applicantId, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("file must not be empty");
        }
        UploadApplicantDocumentCommand command =
                new UploadApplicantDocumentCommand(applicantId, DocumentType.PASSPORT, readBytes(file));
        DocumentExtractionResult result = documentIntelligenceService.uploadAndExtract(command);
        return ApiResponse.success(documentUploadMapper.toResponse(result));
    }

    @PostMapping("/{applicantId}/documents/{documentId}/confirm")
    public ApiResponse<ApplicantProfileResponse> confirmExtractedFields(
            @PathVariable UUID applicantId, @PathVariable UUID documentId,
            @Valid @RequestBody ConfirmExtractedFieldsRequest request) {
        ConfirmExtractedFieldsCommand command = documentUploadMapper.toCommand(applicantId, documentId, request);
        ApplicantProfileResult result = extractedFieldConfirmationService.confirm(command);
        return ApiResponse.success(applicantProfileMapper.toResponse(result));
    }

    @PostMapping("/{applicantId}/documents/passport/vision-preview")
    public ApiResponse<VisionPreviewResponse> previewVisionExtraction(
            @PathVariable UUID applicantId, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("file must not be empty");
        }
        VisualPassportData result = visionExtractionPort.extract(readBytes(file));
        return ApiResponse.success(visionPreviewMapper.toResponse(result));
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
    }
}
