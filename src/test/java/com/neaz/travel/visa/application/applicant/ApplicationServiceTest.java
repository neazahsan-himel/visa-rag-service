package com.neaz.travel.visa.application.applicant;

import com.neaz.travel.visa.application.command.CreateApplicationCommand;
import com.neaz.travel.visa.application.result.ApplicationResult;
import com.neaz.travel.visa.common.exception.ApplicationNotFoundException;
import com.neaz.travel.visa.common.exception.VisaRagException;
import com.neaz.travel.visa.domain.model.Application;
import com.neaz.travel.visa.domain.model.ApplicationStatus;
import com.neaz.travel.visa.domain.repository.ApplicationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link ApplicationService}'s creation (always {@code OPEN}), the
 * ownership-checked withdraw path, and that withdrawing twice (or an
 * application owned by a different applicant) is rejected.
 */
class ApplicationServiceTest {

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final ApplicationService applicationService = new ApplicationService(applicationRepository);

    private Application existingApplication(UUID id, UUID applicantId, ApplicationStatus status) {
        return new Application(id, applicantId, "BD", "IN", "TOURIST", Instant.now(), status);
    }

    @Test
    void create_savesNewOpenApplication() {
        UUID applicantId = UUID.randomUUID();
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationResult result = applicationService.create(
                new CreateApplicationCommand(applicantId, "BD", "IN", "TOURIST"));

        assertEquals(applicantId, result.applicantId());
        assertEquals("BD", result.passportIso());
        assertEquals("IN", result.destinationIso());
        assertEquals("TOURIST", result.visaTypeCode());
        assertEquals(ApplicationStatus.OPEN, result.status());
    }

    @Test
    void withdraw_whenOpen_transitionsToWithdrawn() {
        UUID applicantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.findById(applicationId))
                .thenReturn(Optional.of(existingApplication(applicationId, applicantId, ApplicationStatus.OPEN)));
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationResult result = applicationService.withdraw(applicantId, applicationId);

        assertEquals(ApplicationStatus.WITHDRAWN, result.status());
    }

    @Test
    void withdraw_whenAlreadyWithdrawn_throwsVisaRagException() {
        UUID applicantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.findById(applicationId))
                .thenReturn(Optional.of(existingApplication(applicationId, applicantId, ApplicationStatus.WITHDRAWN)));

        assertThrows(VisaRagException.class, () -> applicationService.withdraw(applicantId, applicationId));
    }

    @Test
    void withdraw_whenOwnedByDifferentApplicant_throwsApplicationNotFoundException() {
        UUID applicantId = UUID.randomUUID();
        UUID otherApplicantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.findById(applicationId))
                .thenReturn(Optional.of(existingApplication(applicationId, otherApplicantId, ApplicationStatus.OPEN)));

        assertThrows(ApplicationNotFoundException.class, () -> applicationService.withdraw(applicantId, applicationId));
    }

    @Test
    void withdraw_whenApplicationDoesNotExist_throwsApplicationNotFoundException() {
        UUID applicantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

        assertThrows(ApplicationNotFoundException.class, () -> applicationService.withdraw(applicantId, applicationId));
    }
}
