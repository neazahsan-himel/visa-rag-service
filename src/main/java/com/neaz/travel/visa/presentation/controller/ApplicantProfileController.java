package com.neaz.travel.visa.presentation.controller;

import com.neaz.travel.visa.application.applicant.ApplicantProfileService;
import com.neaz.travel.visa.application.applicant.ApplicantProfileVerificationService;
import com.neaz.travel.visa.application.command.SetApplicantFieldCommand;
import com.neaz.travel.visa.application.command.VerifyApplicantFieldCommand;
import com.neaz.travel.visa.application.result.ApplicantProfileResult;
import com.neaz.travel.visa.common.model.ApiResponse;
import com.neaz.travel.visa.presentation.dto.ApplicantProfileResponse;
import com.neaz.travel.visa.presentation.dto.SetApplicantFieldRequest;
import com.neaz.travel.visa.presentation.dto.VerifyApplicantFieldRequest;
import com.neaz.travel.visa.presentation.mapper.ApplicantProfileMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Exposes the applicant-profile use cases over HTTP: setting a field,
 * reading the profile, and verifying (confirming/correcting/rejecting) a
 * field. {@code applicantId} is an opaque, unauthenticated identifier for
 * Phase B — see {@code docs/VISA_APPLICATION_ASSISTANT_PROPOSAL.md} §1.
 */
@RestController
@RequestMapping("/api/v1/applicants")
public class ApplicantProfileController {

    private final ApplicantProfileService applicantProfileService;
    private final ApplicantProfileVerificationService applicantProfileVerificationService;
    private final ApplicantProfileMapper applicantProfileMapper;

    public ApplicantProfileController(ApplicantProfileService applicantProfileService,
                                       ApplicantProfileVerificationService applicantProfileVerificationService,
                                       ApplicantProfileMapper applicantProfileMapper) {
        this.applicantProfileService = applicantProfileService;
        this.applicantProfileVerificationService = applicantProfileVerificationService;
        this.applicantProfileMapper = applicantProfileMapper;
    }

    @PostMapping("/{applicantId}/profile/fields")
    public ApiResponse<ApplicantProfileResponse> setField(@PathVariable UUID applicantId,
                                                           @Valid @RequestBody SetApplicantFieldRequest request) {
        SetApplicantFieldCommand command = applicantProfileMapper.toCommand(applicantId, request);
        ApplicantProfileResult result = applicantProfileService.setField(command);
        return ApiResponse.success(applicantProfileMapper.toResponse(result));
    }

    @GetMapping("/{applicantId}/profile")
    public ApiResponse<ApplicantProfileResponse> getProfile(@PathVariable UUID applicantId) {
        ApplicantProfileResult result = applicantProfileService.getProfile(applicantId);
        return ApiResponse.success(applicantProfileMapper.toResponse(result));
    }

    @PostMapping("/{applicantId}/profile/fields/verify")
    public ApiResponse<ApplicantProfileResponse> verifyField(@PathVariable UUID applicantId,
                                                              @Valid @RequestBody VerifyApplicantFieldRequest request) {
        VerifyApplicantFieldCommand command = applicantProfileMapper.toCommand(applicantId, request);
        ApplicantProfileResult result = applicantProfileVerificationService.verify(command);
        return ApiResponse.success(applicantProfileMapper.toResponse(result));
    }
}
