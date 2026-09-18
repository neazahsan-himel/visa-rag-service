package com.neaz.travel.visa.application.applicant;

import com.neaz.travel.visa.application.result.ConsistencyCheckResult;
import com.neaz.travel.visa.common.exception.ApplicantProfileNotFoundException;
import com.neaz.travel.visa.common.exception.ApplicationNotFoundException;
import com.neaz.travel.visa.domain.model.Application;
import com.neaz.travel.visa.domain.model.ApplicantProfile;
import com.neaz.travel.visa.domain.model.ApplicationStatus;
import com.neaz.travel.visa.domain.model.ExtractedField;
import com.neaz.travel.visa.domain.model.VerificationStatus;
import com.neaz.travel.visa.domain.repository.ApplicantProfileRepository;
import com.neaz.travel.visa.domain.repository.ApplicationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link ConsistencyValidationService} surfaces unresolved
 * MRZ-vs-visual conflicts left in the profile's field map by {@code
 * DocumentIntelligenceService} (see {@link ConsistencyCheckResult}'s
 * Javadoc for why this is the only kind of conflict detectable today), and
 * reports a profile with no such keys as consistent.
 */
class ConsistencyValidationServiceTest {

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final ApplicantProfileRepository applicantProfileRepository = mock(ApplicantProfileRepository.class);
    private final ConsistencyValidationService service =
            new ConsistencyValidationService(applicationRepository, applicantProfileRepository);

    private Application ownedApplication(UUID applicationId, UUID applicantId) {
        return new Application(applicationId, applicantId, "BD", "IN", "TOURIST", Instant.now(), ApplicationStatus.OPEN);
    }

    @Test
    void checkConsistency_withNoConflictKeys_returnsConsistentWithNoConflicts() {
        UUID applicantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(ownedApplication(applicationId, applicantId)));
        Instant now = Instant.now();
        ApplicantProfile profile = new ApplicantProfile(UUID.randomUUID(), applicantId, now, now,
                Map.of(ApplicantProfile.PATH_PASSPORT_NUMBER, ExtractedField.selfDeclared("A1234567")), null);
        when(applicantProfileRepository.findByApplicantId(applicantId)).thenReturn(Optional.of(profile));

        ConsistencyCheckResult result = service.checkConsistency(applicantId, applicationId);

        assertTrue(result.consistent());
        assertTrue(result.conflicts().isEmpty());
    }

    @Test
    void checkConsistency_withUnresolvedMrzVisualConflict_returnsThatConflict() {
        UUID applicantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(ownedApplication(applicationId, applicantId)));

        ExtractedField<String> mrzReading = new ExtractedField<>("JANE DOE", "JANE DOE", 0.95, documentId, VerificationStatus.UNVERIFIED);
        ExtractedField<String> visualReading = new ExtractedField<>("JANE DOEE", "JANE DOEE", 0.70, documentId, VerificationStatus.UNVERIFIED);
        Instant now = Instant.now();
        ApplicantProfile profile = new ApplicantProfile(UUID.randomUUID(), applicantId, now, now, Map.of(
                ApplicantProfile.PATH_PERSONAL_FULL_NAME + ApplicantProfile.CONFLICT_SUFFIX_MRZ, mrzReading,
                ApplicantProfile.PATH_PERSONAL_FULL_NAME + ApplicantProfile.CONFLICT_SUFFIX_VISUAL, visualReading
        ), null);
        when(applicantProfileRepository.findByApplicantId(applicantId)).thenReturn(Optional.of(profile));

        ConsistencyCheckResult result = service.checkConsistency(applicantId, applicationId);

        assertTrue(result.conflicts().size() == 1);
        ConsistencyCheckResult.FieldConflict conflict = result.conflicts().get(0);
        assertEquals(ApplicantProfile.PATH_PERSONAL_FULL_NAME, conflict.fieldPath());
        assertEquals("JANE DOE", conflict.mrzReading().normalizedValue());
        assertEquals("JANE DOEE", conflict.visualReading().normalizedValue());
        assertEquals(false, result.consistent());
    }

    @Test
    void checkConsistency_whenApplicationOwnedByDifferentApplicant_throwsApplicationNotFoundException() {
        UUID applicantId = UUID.randomUUID();
        UUID otherApplicantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.findById(applicationId))
                .thenReturn(Optional.of(ownedApplication(applicationId, otherApplicantId)));

        assertThrows(ApplicationNotFoundException.class, () -> service.checkConsistency(applicantId, applicationId));
    }

    @Test
    void checkConsistency_whenProfileDoesNotExist_throwsApplicantProfileNotFoundException() {
        UUID applicantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(ownedApplication(applicationId, applicantId)));
        when(applicantProfileRepository.findByApplicantId(applicantId)).thenReturn(Optional.empty());

        assertThrows(ApplicantProfileNotFoundException.class, () -> service.checkConsistency(applicantId, applicationId));
    }
}
