package com.neaz.travel.visa.presentation.controller;

import com.neaz.travel.visa.application.command.IngestDocumentCommand;
import com.neaz.travel.visa.application.ingestion.DocumentIngestionService;
import com.neaz.travel.visa.application.result.IngestionResult;
import com.neaz.travel.visa.common.model.ApiResponse;
import com.neaz.travel.visa.presentation.dto.IngestionRequest;
import com.neaz.travel.visa.presentation.dto.IngestionResponse;
import com.neaz.travel.visa.presentation.mapper.IngestionMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the document ingestion use case over HTTP.
 */
@RestController
@RequestMapping("/api/v1/ingestion")
public class IngestionController {

    private final DocumentIngestionService documentIngestionService;
    private final IngestionMapper ingestionMapper;

    public IngestionController(DocumentIngestionService documentIngestionService, IngestionMapper ingestionMapper) {
        this.documentIngestionService = documentIngestionService;
        this.ingestionMapper = ingestionMapper;
    }

    @PostMapping("/documents")
    public ApiResponse<IngestionResponse> ingestDocument(@Valid @RequestBody IngestionRequest request) {
        IngestDocumentCommand command = ingestionMapper.toCommand(request);
        IngestionResult result = documentIngestionService.ingest(command);
        return ApiResponse.success(ingestionMapper.toResponse(result));
    }
}