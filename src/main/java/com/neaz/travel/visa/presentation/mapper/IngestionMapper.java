package com.neaz.travel.visa.presentation.mapper;

import com.neaz.travel.visa.application.command.IngestDocumentCommand;
import com.neaz.travel.visa.application.result.IngestionResult;
import com.neaz.travel.visa.domain.model.DocumentMetadata;
import com.neaz.travel.visa.presentation.dto.IngestionRequest;
import com.neaz.travel.visa.presentation.dto.IngestionResponse;
import org.springframework.stereotype.Component;

/**
 * Translates between ingestion DTOs and the application layer's command and
 * result types.
 */
@Component
public class IngestionMapper {

    public IngestDocumentCommand toCommand(IngestionRequest request) {
        DocumentMetadata metadata = new DocumentMetadata(
                request.metadata().destinationIso(),
                request.metadata().passportIso(),
                request.metadata().visaTypeCode(),
                request.metadata().sourceUrl(),
                request.metadata().lastUpdated(),
                request.metadata().version());

        return new IngestDocumentCommand(request.path(), request.title(), metadata);
    }

    public IngestionResponse toResponse(IngestionResult result) {
        return new IngestionResponse(result.documentId(), result.chunkCount(), result.status().name());
    }
}