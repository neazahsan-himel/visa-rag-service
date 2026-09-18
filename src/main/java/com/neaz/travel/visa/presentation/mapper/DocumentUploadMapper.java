package com.neaz.travel.visa.presentation.mapper;

import com.neaz.travel.visa.application.command.ConfirmExtractedFieldsCommand;
import com.neaz.travel.visa.application.result.DocumentExtractionResult;
import com.neaz.travel.visa.domain.model.ExtractedField;
import com.neaz.travel.visa.presentation.dto.ConfirmExtractedFieldsRequest;
import com.neaz.travel.visa.presentation.dto.DocumentUploadResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Translates {@link DocumentExtractionResult} into the applicant-document upload response DTO. */
@Component
public class DocumentUploadMapper {

    public DocumentUploadResponse toResponse(DocumentExtractionResult result) {
        Map<String, DocumentUploadResponse.FieldCandidateDto> candidates = result.fieldCandidates().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> toDto(entry.getValue())));
        List<String> issues = result.issues().stream()
                .map(Enum::name)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new DocumentUploadResponse(result.documentId(), result.status().name(), result.message(), issues, candidates);
    }

    public ConfirmExtractedFieldsCommand toCommand(UUID applicantId, UUID documentId, ConfirmExtractedFieldsRequest request) {
        return new ConfirmExtractedFieldsCommand(applicantId, documentId, request.fields());
    }

    private DocumentUploadResponse.FieldCandidateDto toDto(ExtractedField<?> field) {
        return new DocumentUploadResponse.FieldCandidateDto(
                field.normalizedValue(), field.confidence(), field.verificationStatus().name());
    }
}
