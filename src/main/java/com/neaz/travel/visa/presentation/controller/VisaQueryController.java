package com.neaz.travel.visa.presentation.controller;

import com.neaz.travel.visa.application.command.VisaQueryCommand;
import com.neaz.travel.visa.application.query.VisaQueryService;
import com.neaz.travel.visa.application.result.VisaQueryResult;
import com.neaz.travel.visa.common.model.ApiResponse;
import com.neaz.travel.visa.presentation.dto.VisaQueryRequest;
import com.neaz.travel.visa.presentation.dto.VisaQueryResponse;
import com.neaz.travel.visa.presentation.mapper.VisaQueryMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the visa query use case over HTTP.
 */
@RestController
@RequestMapping("/api/v1/visa")
public class VisaQueryController {

    private final VisaQueryService visaQueryService;
    private final VisaQueryMapper visaQueryMapper;

    public VisaQueryController(VisaQueryService visaQueryService, VisaQueryMapper visaQueryMapper) {
        this.visaQueryService = visaQueryService;
        this.visaQueryMapper = visaQueryMapper;
    }

    @PostMapping("/query")
    public ApiResponse<VisaQueryResponse> query(@Valid @RequestBody VisaQueryRequest request) {
        VisaQueryCommand command = visaQueryMapper.toCommand(request);
        VisaQueryResult result = visaQueryService.answer(command);
        return ApiResponse.success(visaQueryMapper.toResponse(result));
    }
}