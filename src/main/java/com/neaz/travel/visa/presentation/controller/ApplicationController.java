package com.neaz.travel.visa.presentation.controller;

import com.neaz.travel.visa.application.applicant.ApplicationService;
import com.neaz.travel.visa.application.applicant.ChecklistService;
import com.neaz.travel.visa.application.applicant.ConsistencyValidationService;
import com.neaz.travel.visa.application.command.CreateApplicationCommand;
import com.neaz.travel.visa.application.result.ApplicationResult;
import com.neaz.travel.visa.application.result.ChecklistResult;
import com.neaz.travel.visa.application.result.ConsistencyCheckResult;
import com.neaz.travel.visa.common.model.ApiResponse;
import com.neaz.travel.visa.presentation.dto.ApplicationResponse;
import com.neaz.travel.visa.presentation.dto.ChecklistResponse;
import com.neaz.travel.visa.presentation.dto.ConsistencyCheckResponse;
import com.neaz.travel.visa.presentation.dto.CreateApplicationRequest;
import com.neaz.travel.visa.presentation.mapper.ApplicationMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Exposes the Phase D visa-application use cases over HTTP: creating and
 * withdrawing an application, the document checklist, and the profile
 * consistency check. {@code applicantId} is an opaque, unauthenticated
 * identifier, same posture as the rest of the Visa Application Assistant —
 * see {@code docs/VISA_APPLICATION_ASSISTANT_PROPOSAL.md} §9 Phase D.
 */
@RestController
@RequestMapping("/api/v1/applicants/{applicantId}/applications")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ChecklistService checklistService;
    private final ConsistencyValidationService consistencyValidationService;
    private final ApplicationMapper applicationMapper;

    public ApplicationController(ApplicationService applicationService,
                                  ChecklistService checklistService,
                                  ConsistencyValidationService consistencyValidationService,
                                  ApplicationMapper applicationMapper) {
        this.applicationService = applicationService;
        this.checklistService = checklistService;
        this.consistencyValidationService = consistencyValidationService;
        this.applicationMapper = applicationMapper;
    }

    @PostMapping
    public ApiResponse<ApplicationResponse> createApplication(
            @PathVariable UUID applicantId, @Valid @RequestBody CreateApplicationRequest request) {
        CreateApplicationCommand command = applicationMapper.toCommand(applicantId, request);
        ApplicationResult result = applicationService.create(command);
        return ApiResponse.success(applicationMapper.toResponse(result));
    }

    @PostMapping("/{applicationId}/withdraw")
    public ApiResponse<ApplicationResponse> withdrawApplication(
            @PathVariable UUID applicantId, @PathVariable UUID applicationId) {
        ApplicationResult result = applicationService.withdraw(applicantId, applicationId);
        return ApiResponse.success(applicationMapper.toResponse(result));
    }

    @GetMapping("/{applicationId}/checklist")
    public ApiResponse<ChecklistResponse> getChecklist(
            @PathVariable UUID applicantId, @PathVariable UUID applicationId) {
        ChecklistResult result = checklistService.getChecklist(applicantId, applicationId);
        return ApiResponse.success(applicationMapper.toResponse(result));
    }

    @GetMapping("/{applicationId}/consistency-check")
    public ApiResponse<ConsistencyCheckResponse> getConsistencyCheck(
            @PathVariable UUID applicantId, @PathVariable UUID applicationId) {
        ConsistencyCheckResult result = consistencyValidationService.checkConsistency(applicantId, applicationId);
        return ApiResponse.success(applicationMapper.toResponse(result));
    }
}
