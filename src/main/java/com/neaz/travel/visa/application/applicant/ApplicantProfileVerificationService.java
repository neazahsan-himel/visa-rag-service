package com.neaz.travel.visa.application.applicant;

import com.neaz.travel.visa.application.command.VerifyApplicantFieldCommand;
import com.neaz.travel.visa.application.result.ApplicantProfileResult;
import com.neaz.travel.visa.common.exception.ApplicantProfileNotFoundException;
import com.neaz.travel.visa.domain.model.ApplicantProfile;
import com.neaz.travel.visa.domain.repository.ApplicantProfileRepository;
import org.springframework.stereotype.Service;

/**
 * Applies a verification decision — confirm, correct, or reject — to one
 * field of an applicant's profile. This is the only entry point that moves a
 * field out of {@code UNVERIFIED}; nothing downstream may treat a field as
 * trustworthy until it has gone through here.
 */
@Service
public class ApplicantProfileVerificationService {

    private final ApplicantProfileRepository applicantProfileRepository;

    public ApplicantProfileVerificationService(ApplicantProfileRepository applicantProfileRepository) {
        this.applicantProfileRepository = applicantProfileRepository;
    }

    /**
     * @throws ApplicantProfileNotFoundException if the applicant has no profile yet
     * @throws IllegalStateException             if the field named by the command hasn't been set yet
     */
    public ApplicantProfileResult verify(VerifyApplicantFieldCommand command) {
        ApplicantProfile profile = applicantProfileRepository.findByApplicantId(command.applicantId())
                .orElseThrow(() -> new ApplicantProfileNotFoundException(command.applicantId()));

        switch (command.action()) {
            case CONFIRM -> profile.confirmField(command.fieldPath());
            case REJECT -> profile.rejectField(command.fieldPath());
            case CORRECT -> profile.correctField(command.fieldPath(),
                    ApplicantFieldValueParser.parseTypedValue(command.fieldPath(), command.correctedValue()));
        }

        return ApplicantProfileResult.from(applicantProfileRepository.save(profile));
    }
}
