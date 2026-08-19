package com.neaz.travel.visa.presentation.mapper;

import com.neaz.travel.visa.application.command.VisaQueryCommand;
import com.neaz.travel.visa.application.result.VisaQueryResult;
import com.neaz.travel.visa.domain.model.Citation;
import com.neaz.travel.visa.domain.model.MetadataFilter;
import com.neaz.travel.visa.presentation.dto.VisaQueryRequest;
import com.neaz.travel.visa.presentation.dto.VisaQueryResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Translates between visa query DTOs and the application layer's command and
 * result types.
 */
@Component
public class VisaQueryMapper {

    public VisaQueryCommand toCommand(VisaQueryRequest request) {
        MetadataFilter filter = new MetadataFilter(request.passport(), request.destination(), request.visaType());
        return new VisaQueryCommand(request.query(), filter);
    }

    public VisaQueryResponse toResponse(VisaQueryResult result) {
        List<VisaQueryResponse.CitationDto> citations = result.citations().stream()
                .map(this::toCitationDto)
                .toList();
        return new VisaQueryResponse(result.answerText(), citations, result.confidenceScore());
    }

    private VisaQueryResponse.CitationDto toCitationDto(Citation citation) {
        return new VisaQueryResponse.CitationDto(
                citation.ref(), citation.source(), citation.url(), citation.lastUpdated(), citation.chunkId());
    }
}