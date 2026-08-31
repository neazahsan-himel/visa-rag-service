package com.neaz.travel.visa.application.applicant;

import com.neaz.travel.visa.application.command.SetApplicantFieldCommand;
import com.neaz.travel.visa.application.result.ApplicantProfileResult;
import com.neaz.travel.visa.common.exception.ApplicantProfileNotFoundException;
import com.neaz.travel.visa.domain.model.ApplicantProfile;
import com.neaz.travel.visa.domain.repository.ApplicantProfileRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link ApplicantProfileService}'s find-or-create semantics, the
 * {@code purposeOfVisit} routing to the plain field rather than the generic
 * map, and the not-found path on {@code getProfile}.
 */
class ApplicantProfileServiceTest {

    private final ApplicantProfileRepository applicantProfileRepository = mock(ApplicantProfileRepository.class);
    private final ApplicantProfileService applicantProfileService = new ApplicantProfileService(applicantProfileRepository);

    @Test
    void setField_whenNoExistingProfile_createsAndSavesNewProfileWithField() {
        UUID applicantId = UUID.randomUUID();
        when(applicantProfileRepository.findByApplicantId(applicantId)).thenReturn(Optional.empty());
        when(applicantProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicantProfileResult result = applicantProfileService.setField(
                new SetApplicantFieldCommand(applicantId, ApplicantProfile.PATH_PERSONAL_FULL_NAME, "Jane Doe"));

        assertEquals(applicantId, result.applicantId());
        assertEquals("Jane Doe", result.personalInfo().fullName().normalizedValue());
    }

    @Test
    void setField_whenExistingProfile_reusesExistingProfileId() {
        UUID applicantId = UUID.randomUUID();
        Instant now = Instant.now();
        ApplicantProfile existing = new ApplicantProfile(UUID.randomUUID(), applicantId, now, now, Map.of(), null);
        when(applicantProfileRepository.findByApplicantId(applicantId)).thenReturn(Optional.of(existing));
        when(applicantProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicantProfileResult result = applicantProfileService.setField(
                new SetApplicantFieldCommand(applicantId, ApplicantProfile.PATH_PERSONAL_FULL_NAME, "Jane Doe"));

        assertEquals(existing.getId(), result.id());
    }

    @Test
    void setField_forPurposeOfVisitPath_setsPlainPurposeOfVisitField() {
        UUID applicantId = UUID.randomUUID();
        when(applicantProfileRepository.findByApplicantId(applicantId)).thenReturn(Optional.empty());
        when(applicantProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicantProfileResult result = applicantProfileService.setField(
                new SetApplicantFieldCommand(applicantId, ApplicantProfile.PATH_PURPOSE_OF_VISIT, "Tourism"));

        assertEquals("Tourism", result.travelInfo().purposeOfVisit());
    }

    @Test
    void getProfile_whenProfileExists_returnsMappedResult() {
        UUID applicantId = UUID.randomUUID();
        Instant now = Instant.now();
        ApplicantProfile existing = new ApplicantProfile(UUID.randomUUID(), applicantId, now, now, Map.of(), null);
        when(applicantProfileRepository.findByApplicantId(applicantId)).thenReturn(Optional.of(existing));

        ApplicantProfileResult result = applicantProfileService.getProfile(applicantId);

        assertEquals(existing.getId(), result.id());
        verify(applicantProfileRepository).findByApplicantId(applicantId);
    }

    @Test
    void getProfile_whenProfileDoesNotExist_throwsApplicantProfileNotFoundException() {
        UUID applicantId = UUID.randomUUID();
        when(applicantProfileRepository.findByApplicantId(applicantId)).thenReturn(Optional.empty());

        assertThrows(ApplicantProfileNotFoundException.class, () -> applicantProfileService.getProfile(applicantId));
    }
}
