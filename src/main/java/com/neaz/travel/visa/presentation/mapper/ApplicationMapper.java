package com.neaz.travel.visa.presentation.mapper;

import com.neaz.travel.visa.application.command.CreateApplicationCommand;
import com.neaz.travel.visa.application.result.ApplicationResult;
import com.neaz.travel.visa.application.result.ChecklistResult;
import com.neaz.travel.visa.application.result.ConsistencyCheckResult;
import com.neaz.travel.visa.domain.model.Citation;
import com.neaz.travel.visa.domain.model.ExtractedField;
import com.neaz.travel.visa.presentation.dto.ApplicationResponse;
import com.neaz.travel.visa.presentation.dto.ChecklistResponse;
import com.neaz.travel.visa.presentation.dto.ConsistencyCheckResponse;
import com.neaz.travel.visa.presentation.dto.CreateApplicationRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Translates between application DTOs and the application layer's command
 * and result types.
 */
@Component
public class ApplicationMapper {

    public CreateApplicationCommand toCommand(UUID applicantId, CreateApplicationRequest request) {
        return new CreateApplicationCommand(applicantId, request.passportIso(), request.destinationIso(), request.visaTypeCode());
    }

    public ApplicationResponse toResponse(ApplicationResult result) {
        return new ApplicationResponse(result.id(), result.applicantId(), result.passportIso(), result.destinationIso(),
                result.visaTypeCode(), result.status().name(), result.createdAt());
    }

    public ChecklistResponse toResponse(ChecklistResult result) {
        List<ChecklistResponse.CitationDto> citations = result.citations().stream()
                .map(this::toCitationDto)
                .toList();
        return new ChecklistResponse(result.applicationId(), result.checklistText(), citations, result.confidenceScore());
    }

    public ConsistencyCheckResponse toResponse(ConsistencyCheckResult result) {
        List<ConsistencyCheckResponse.FieldConflictDto> conflicts = result.conflicts().stream()
                .map(this::toFieldConflictDto)
                .toList();
        return new ConsistencyCheckResponse(result.applicantId(), result.consistent(), conflicts);
    }

    private ChecklistResponse.CitationDto toCitationDto(Citation citation) {
        return new ChecklistResponse.CitationDto(
                citation.ref(), citation.source(), citation.url(), citation.lastUpdated(), citation.chunkId());
    }

    private ConsistencyCheckResponse.FieldConflictDto toFieldConflictDto(ConsistencyCheckResult.FieldConflict conflict) {
        return new ConsistencyCheckResponse.FieldConflictDto(
                conflict.fieldPath(), normalizedValue(conflict.mrzReading()), normalizedValue(conflict.visualReading()));
    }

    private Object normalizedValue(ExtractedField<?> field) {
        return field.normalizedValue();
    }
}
