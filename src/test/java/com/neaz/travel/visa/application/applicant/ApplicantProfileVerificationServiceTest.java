package com.neaz.travel.visa.application.applicant;

import com.neaz.travel.visa.application.command.VerifyApplicantFieldCommand;
import com.neaz.travel.visa.application.result.ApplicantProfileResult;
import com.neaz.travel.visa.common.exception.ApplicantProfileNotFoundException;
import com.neaz.travel.visa.common.exception.VisaRagException;
import com.neaz.travel.visa.domain.model.ApplicantProfile;
import com.neaz.travel.visa.domain.model.ExtractedField;
import com.neaz.travel.visa.domain.model.VerificationStatus;
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
import static org.mockito.Mockito.when;

/**
 * Verifies {@link ApplicantProfileVerificationService} dispatches each
 * {@link VerifyApplicantFieldCommand.Action} to the right {@code
 * ApplicantProfile} transition, and that not-found / not-set errors
 * propagate rather than being swallowed.
 */
class ApplicantProfileVerificationServiceTest {

    private final ApplicantProfileRepository applicantProfileRepository = mock(ApplicantProfileRepository.class);
    private final ApplicantProfileVerificationService verificationService =
            new ApplicantProfileVerificationService(applicantProfileRepository);

    private ApplicantProfile profileWithUnverifiedFullName(UUID applicantId) {
        Instant now = Instant.now();
        ApplicantProfile profile = new ApplicantProfile(UUID.randomUUID(), applicantId, now, now, Map.of(), null);
        profile.setField(ApplicantProfile.PATH_PERSONAL_FULL_NAME,
                new ExtractedField<>("Jane", "Jane", 0.7, UUID.randomUUID(), VerificationStatus.UNVERIFIED));
        return profile;
    }

    @Test
    void verify_withConfirmAction_confirmsField() {
        UUID applicantId = UUID.randomUUID();
        ApplicantProfile profile = profileWithUnverifiedFullName(applicantId);
        when(applicantProfileRepository.findByApplicantId(applicantId)).thenReturn(Optional.of(profile));
        when(applicantProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicantProfileResult result = verificationService.verify(new VerifyApplicantFieldCommand(
                applicantId, ApplicantProfile.PATH_PERSONAL_FULL_NAME, VerifyApplicantFieldCommand.Action.CONFIRM, null));

        assertEquals(VerificationStatus.USER_CONFIRMED, result.personalInfo().fullName().verificationStatus());
    }

    @Test
    void verify_withCorrectAction_correctsFieldWithParsedValue() {
        UUID applicantId = UUID.randomUUID();
        ApplicantProfile profile = profileWithUnverifiedFullName(applicantId);
        when(applicantProfileRepository.findByApplicantId(applicantId)).thenReturn(Optional.of(profile));
        when(applicantProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicantProfileResult result = verificationService.verify(new VerifyApplicantFieldCommand(
                applicantId, ApplicantProfile.PATH_PERSONAL_FULL_NAME,
                VerifyApplicantFieldCommand.Action.CORRECT, "Jane Doe"));

        assertEquals("Jane Doe", result.personalInfo().fullName().normalizedValue());
        assertEquals(VerificationStatus.USER_CORRECTED, result.personalInfo().fullName().verificationStatus());
    }

    @Test
    void verify_withRejectAction_rejectsField() {
        UUID applicantId = UUID.randomUUID();
        ApplicantProfile profile = profileWithUnverifiedFullName(applicantId);
        when(applicantProfileRepository.findByApplicantId(applicantId)).thenReturn(Optional.of(profile));
        when(applicantProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicantProfileResult result = verificationService.verify(new VerifyApplicantFieldCommand(
                applicantId, ApplicantProfile.PATH_PERSONAL_FULL_NAME, VerifyApplicantFieldCommand.Action.REJECT, null));

        assertEquals(VerificationStatus.REJECTED, result.personalInfo().fullName().verificationStatus());
    }

    @Test
    void verify_whenProfileDoesNotExist_throwsApplicantProfileNotFoundException() {
        UUID applicantId = UUID.randomUUID();
        when(applicantProfileRepository.findByApplicantId(applicantId)).thenReturn(Optional.empty());

        assertThrows(ApplicantProfileNotFoundException.class, () -> verificationService.verify(
                new VerifyApplicantFieldCommand(applicantId, ApplicantProfile.PATH_PERSONAL_FULL_NAME,
                        VerifyApplicantFieldCommand.Action.CONFIRM, null)));
    }

    @Test
    void verify_whenFieldWasNeverSet_throwsVisaRagException() {
        UUID applicantId = UUID.randomUUID();
        Instant now = Instant.now();
        ApplicantProfile emptyProfile = new ApplicantProfile(UUID.randomUUID(), applicantId, now, now, Map.of(), null);
        when(applicantProfileRepository.findByApplicantId(applicantId)).thenReturn(Optional.of(emptyProfile));

        assertThrows(VisaRagException.class, () -> verificationService.verify(
                new VerifyApplicantFieldCommand(applicantId, ApplicantProfile.PATH_PERSONAL_FULL_NAME,
                        VerifyApplicantFieldCommand.Action.CONFIRM, null)));
    }
}
