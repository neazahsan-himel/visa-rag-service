package com.neaz.travel.visa.application.applicant;

import com.neaz.travel.visa.application.command.SetApplicantFieldCommand;
import com.neaz.travel.visa.application.result.ApplicantProfileResult;
import com.neaz.travel.visa.common.exception.ApplicantProfileNotFoundException;
import com.neaz.travel.visa.domain.model.ApplicantProfile;
import com.neaz.travel.visa.domain.model.ExtractedField;
import com.neaz.travel.visa.domain.repository.ApplicantProfileRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates reading and writing an {@link ApplicantProfile}: finds or
 * lazily creates the profile for an applicant, converts a submitted value
 * into the target field's declared type, and persists the result. Contains
 * no persistence logic itself — that lives behind {@link ApplicantProfileRepository}.
 */
@Service
public class ApplicantProfileService {

    private final ApplicantProfileRepository applicantProfileRepository;

    public ApplicantProfileService(ApplicantProfileRepository applicantProfileRepository) {
        this.applicantProfileRepository = applicantProfileRepository;
    }

    /**
     * Sets one field on the applicant's profile, creating the profile first
     * if this is its first field. Every value set this way is self-declared
     * (no source document) and immediately {@code USER_CONFIRMED} — see
     * {@link ExtractedField#selfDeclared}.
     */
    public ApplicantProfileResult setField(SetApplicantFieldCommand command) {
        ApplicantProfile profile = applicantProfileRepository.findByApplicantId(command.applicantId())
                .orElseGet(() -> newProfile(command.applicantId()));

        if (ApplicantProfile.PATH_PURPOSE_OF_VISIT.equals(command.fieldPath())) {
            profile.setPurposeOfVisit((String) ApplicantFieldValueParser.parseTypedValue(
                    ApplicantProfile.PATH_PURPOSE_OF_VISIT, command.value()));
        } else {
            Object typedValue = ApplicantFieldValueParser.parseTypedValue(command.fieldPath(), command.value());
            profile.setField(command.fieldPath(), ExtractedField.selfDeclared(typedValue));
        }

        return ApplicantProfileResult.from(applicantProfileRepository.save(profile));
    }

    /**
     * @throws ApplicantProfileNotFoundException if the applicant has no profile yet
     */
    public ApplicantProfileResult getProfile(UUID applicantId) {
        ApplicantProfile profile = applicantProfileRepository.findByApplicantId(applicantId)
                .orElseThrow(() -> new ApplicantProfileNotFoundException(applicantId));
        return ApplicantProfileResult.from(profile);
    }

    private ApplicantProfile newProfile(UUID applicantId) {
        Instant now = Instant.now();
        return new ApplicantProfile(UUID.randomUUID(), applicantId, now, now, Map.of(), null);
    }
}
