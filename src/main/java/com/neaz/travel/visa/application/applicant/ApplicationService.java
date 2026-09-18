package com.neaz.travel.visa.application.applicant;

import com.neaz.travel.visa.application.command.CreateApplicationCommand;
import com.neaz.travel.visa.application.result.ApplicationResult;
import com.neaz.travel.visa.common.exception.ApplicationNotFoundException;
import com.neaz.travel.visa.domain.model.Application;
import com.neaz.travel.visa.domain.model.ApplicationStatus;
import com.neaz.travel.visa.domain.repository.ApplicationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates creating and withdrawing an {@link Application}. Contains no
 * persistence logic itself — that lives behind {@link ApplicationRepository}.
 */
@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;

    public ApplicationService(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    public ApplicationResult create(CreateApplicationCommand command) {
        Application application = new Application(UUID.randomUUID(), command.applicantId(), command.passportIso(),
                command.destinationIso(), command.visaTypeCode(), Instant.now(), ApplicationStatus.OPEN);
        return ApplicationResult.from(applicationRepository.save(application));
    }

    /**
     * @throws ApplicationNotFoundException if no application with {@code applicationId} is owned by {@code applicantId}
     */
    public ApplicationResult withdraw(UUID applicantId, UUID applicationId) {
        Application application = findOwned(applicantId, applicationId);
        application.withdraw();
        return ApplicationResult.from(applicationRepository.save(application));
    }

    /**
     * @throws ApplicationNotFoundException if no application with {@code applicationId} is owned by {@code applicantId}
     */
    public ApplicationResult getOwned(UUID applicantId, UUID applicationId) {
        return ApplicationResult.from(findOwned(applicantId, applicationId));
    }

    private Application findOwned(UUID applicantId, UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .filter(candidate -> candidate.getApplicantId().equals(applicantId))
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
    }
}
