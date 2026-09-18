package com.neaz.travel.visa.application.document;

import com.neaz.travel.visa.application.command.UploadApplicantDocumentCommand;
import com.neaz.travel.visa.application.port.DocumentStoragePort;
import com.neaz.travel.visa.application.port.ImageQualityPort;
import com.neaz.travel.visa.application.port.OcrPort;
import com.neaz.travel.visa.application.port.VisionExtractionPort;
import com.neaz.travel.visa.application.result.DocumentExtractionResult;
import com.neaz.travel.visa.domain.model.ApplicantDocument;
import com.neaz.travel.visa.domain.model.ApplicantProfile;
import com.neaz.travel.visa.domain.model.DocumentProcessingStatus;
import com.neaz.travel.visa.domain.model.DocumentType;
import com.neaz.travel.visa.domain.model.ExtractedField;
import com.neaz.travel.visa.domain.model.ImageQualityAssessment;
import com.neaz.travel.visa.domain.model.ImageQualityIssue;
import com.neaz.travel.visa.domain.model.VerificationStatus;
import com.neaz.travel.visa.domain.model.VisualFieldReading;
import com.neaz.travel.visa.domain.model.VisualPassportData;
import com.neaz.travel.visa.domain.repository.ApplicantDocumentRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the behavior added on top of the pre-existing MRZ-only pipeline:
 * MRZ vs. visual confidence tiers (heuristic, per {@code
 * DocumentIntelligenceService}'s constants), visual field validation,
 * cross-field agreement/conflict handling, and the invariant that every
 * candidate this service produces starts {@code UNVERIFIED} regardless of
 * confidence.
 *
 * <p>MRZ fixtures are built by hand (not through real OCR) with check
 * digits computed the same way {@code MrzParser} validates them, so each
 * test can deliberately corrupt exactly one check digit and know precisely
 * which confidence tier that field must land in.
 */
class DocumentIntelligenceServiceTest {

    // Baseline TD3 MRZ: passport A1234567, DOB 1990-05-15, expiry 2030-05-15, all check digits valid.
    private static final String VALID_LINE_1 = "P<BGDDOE<<JOHN<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<";
    private static final String VALID_LINE_2 = "A1234567<6BGD9005156M3005154<<<<<<<<<<<<<<<8";

    private static final int PASSPORT_CHECK_DIGIT_INDEX = 9;
    private static final int DOB_CHECK_DIGIT_INDEX = 19;
    private static final int EXPIRY_CHECK_DIGIT_INDEX = 27;
    private static final int COMPOSITE_CHECK_DIGIT_INDEX = 43;

    private final ImageQualityPort imageQualityPort = mock(ImageQualityPort.class);
    private final OcrPort ocrPort = mock(OcrPort.class);
    private final VisionExtractionPort visionExtractionPort = mock(VisionExtractionPort.class);
    private final DocumentStoragePort documentStoragePort = mock(DocumentStoragePort.class);
    private final ApplicantDocumentRepository applicantDocumentRepository = mock(ApplicantDocumentRepository.class);

    private final DocumentIntelligenceService service = new DocumentIntelligenceService(
            imageQualityPort, ocrPort, visionExtractionPort, documentStoragePort, applicantDocumentRepository);

    private UploadApplicantDocumentCommand command() {
        return new UploadApplicantDocumentCommand(UUID.randomUUID(), DocumentType.PASSPORT, new byte[] {1, 2, 3});
    }

    private void stubHappyPathUpTo(String mrzLine2) {
        when(documentStoragePort.store(any(), any(), any())).thenReturn("storage/path");
        when(imageQualityPort.assess(any())).thenReturn(ImageQualityAssessment.accepted());
        when(ocrPort.extractText(any())).thenReturn(VALID_LINE_1 + "\n" + mrzLine2);
        when(visionExtractionPort.extract(any())).thenReturn(VisualPassportData.empty());
    }

    private static String withCorruptedCheckDigit(String line2, int index) {
        char original = line2.charAt(index);
        char replacement = original == '0' ? '1' : '0';
        return line2.substring(0, index) + replacement + line2.substring(index + 1);
    }

    // ── MRZ field-specific checksum confidence ──────────────────────────────────

    @Test
    void uploadAndExtract_validPassportNumberChecksum_assignsHighConfidence() {
        stubHappyPathUpTo(VALID_LINE_2);

        DocumentExtractionResult result = service.uploadAndExtract(command());

        ExtractedField<?> passportNumber = result.fieldCandidates().get(ApplicantProfile.PATH_PASSPORT_NUMBER);
        assertEquals("A1234567", passportNumber.normalizedValue());
        assertEquals(0.95, passportNumber.confidence());
        assertEquals(VerificationStatus.UNVERIFIED, passportNumber.verificationStatus());
    }

    @Test
    void uploadAndExtract_invalidPassportNumberChecksum_assignsLowConfidence() {
        stubHappyPathUpTo(withCorruptedCheckDigit(VALID_LINE_2, PASSPORT_CHECK_DIGIT_INDEX));

        DocumentExtractionResult result = service.uploadAndExtract(command());

        ExtractedField<?> passportNumber = result.fieldCandidates().get(ApplicantProfile.PATH_PASSPORT_NUMBER);
        assertEquals(0.40, passportNumber.confidence());
        assertEquals(VerificationStatus.UNVERIFIED, passportNumber.verificationStatus());
    }

    @Test
    void uploadAndExtract_validDateOfBirthChecksum_assignsHighConfidence() {
        stubHappyPathUpTo(VALID_LINE_2);

        DocumentExtractionResult result = service.uploadAndExtract(command());

        ExtractedField<?> dob = result.fieldCandidates().get(ApplicantProfile.PATH_PERSONAL_DATE_OF_BIRTH);
        assertEquals(LocalDate.of(1990, 5, 15), dob.normalizedValue());
        assertEquals(0.95, dob.confidence());
    }

    @Test
    void uploadAndExtract_invalidDateOfBirthChecksum_assignsLowConfidence() {
        stubHappyPathUpTo(withCorruptedCheckDigit(VALID_LINE_2, DOB_CHECK_DIGIT_INDEX));

        DocumentExtractionResult result = service.uploadAndExtract(command());

        ExtractedField<?> dob = result.fieldCandidates().get(ApplicantProfile.PATH_PERSONAL_DATE_OF_BIRTH);
        assertEquals(0.40, dob.confidence());
    }

    @Test
    void uploadAndExtract_validExpiryChecksum_assignsHighConfidence() {
        stubHappyPathUpTo(VALID_LINE_2);

        DocumentExtractionResult result = service.uploadAndExtract(command());

        ExtractedField<?> expiry = result.fieldCandidates().get(ApplicantProfile.PATH_PASSPORT_EXPIRY_DATE);
        assertEquals(LocalDate.of(2030, 5, 15), expiry.normalizedValue());
        assertEquals(0.95, expiry.confidence());
    }

    @Test
    void uploadAndExtract_invalidExpiryChecksum_assignsLowConfidence() {
        stubHappyPathUpTo(withCorruptedCheckDigit(VALID_LINE_2, EXPIRY_CHECK_DIGIT_INDEX));

        DocumentExtractionResult result = service.uploadAndExtract(command());

        ExtractedField<?> expiry = result.fieldCandidates().get(ApplicantProfile.PATH_PASSPORT_EXPIRY_DATE);
        assertEquals(0.40, expiry.confidence());
    }

    // ── Composite MRZ checksum (fields with no individual check digit) ─────────

    @Test
    void uploadAndExtract_validCompositeChecksum_assignsHighConfidenceToCompositeBackedFields() {
        stubHappyPathUpTo(VALID_LINE_2);

        DocumentExtractionResult result = service.uploadAndExtract(command());

        assertEquals(0.95, result.fieldCandidates().get(ApplicantProfile.PATH_PERSONAL_FULL_NAME).confidence());
        assertEquals(0.95, result.fieldCandidates().get(ApplicantProfile.PATH_PERSONAL_GENDER).confidence());
        assertEquals(0.95, result.fieldCandidates().get(ApplicantProfile.PATH_PASSPORT_ISSUING_COUNTRY).confidence());
        assertEquals(0.95, result.fieldCandidates().get(ApplicantProfile.PATH_NATIONALITY).confidence());
    }

    @Test
    void uploadAndExtract_invalidCompositeChecksum_assignsLowConfidenceToCompositeBackedFieldsOnly() {
        // Corrupt only the composite digit; every individual field check digit stays valid.
        stubHappyPathUpTo(withCorruptedCheckDigit(VALID_LINE_2, COMPOSITE_CHECK_DIGIT_INDEX));

        DocumentExtractionResult result = service.uploadAndExtract(command());

        assertEquals(0.40, result.fieldCandidates().get(ApplicantProfile.PATH_PERSONAL_FULL_NAME).confidence());
        assertEquals(0.40, result.fieldCandidates().get(ApplicantProfile.PATH_PERSONAL_GENDER).confidence());
        assertEquals(0.40, result.fieldCandidates().get(ApplicantProfile.PATH_PASSPORT_ISSUING_COUNTRY).confidence());
        assertEquals(0.40, result.fieldCandidates().get(ApplicantProfile.PATH_NATIONALITY).confidence());

        // Individually-checked fields are unaffected by the composite failure.
        assertEquals(0.95, result.fieldCandidates().get(ApplicantProfile.PATH_PASSPORT_NUMBER).confidence());
        assertEquals(0.95, result.fieldCandidates().get(ApplicantProfile.PATH_PERSONAL_DATE_OF_BIRTH).confidence());
        assertEquals(0.95, result.fieldCandidates().get(ApplicantProfile.PATH_PASSPORT_EXPIRY_DATE).confidence());
    }

    @Test
    void uploadAndExtract_allMrzCandidates_startUnverifiedRegardlessOfConfidence() {
        stubHappyPathUpTo(VALID_LINE_2);

        DocumentExtractionResult result = service.uploadAndExtract(command());

        assertFalse(result.fieldCandidates().isEmpty());
        for (ExtractedField<?> field : result.fieldCandidates().values()) {
            assertEquals(VerificationStatus.UNVERIFIED, field.verificationStatus());
        }
    }

    // ── Visual extraction ────────────────────────────────────────────────────

    @Test
    void uploadAndExtract_visualFieldClearlyReadableAndValid_assignsHighConfidence() {
        stubHappyPathUpTo(VALID_LINE_2);
        when(visionExtractionPort.extract(any())).thenReturn(new VisualPassportData(
                VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND,
                new VisualFieldReading("2020-01-10", true),
                new VisualFieldReading("Dhaka", true),
                new VisualFieldReading("P", true),
                VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND));

        DocumentExtractionResult result = service.uploadAndExtract(command());

        ExtractedField<?> issueDate = result.fieldCandidates().get(ApplicantProfile.PATH_PASSPORT_ISSUE_DATE);
        assertEquals(LocalDate.of(2020, 1, 10), issueDate.normalizedValue());
        assertEquals(0.90, issueDate.confidence());
        assertEquals(VerificationStatus.UNVERIFIED, issueDate.verificationStatus());

        ExtractedField<?> placeOfBirth = result.fieldCandidates().get(ApplicantProfile.PATH_PERSONAL_PLACE_OF_BIRTH);
        assertEquals("Dhaka", placeOfBirth.normalizedValue());
        assertEquals(0.90, placeOfBirth.confidence());

        ExtractedField<?> passportType = result.fieldCandidates().get(ApplicantProfile.PATH_PASSPORT_TYPE);
        assertEquals("P", passportType.normalizedValue());
        assertEquals(0.90, passportType.confidence());
    }

    @Test
    void uploadAndExtract_visualFieldValidButNotClearlyReadable_assignsMediumConfidence() {
        stubHappyPathUpTo(VALID_LINE_2);
        when(visionExtractionPort.extract(any())).thenReturn(new VisualPassportData(
                VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND,
                VisualFieldReading.NOT_FOUND,
                new VisualFieldReading("Dhaka", false),
                VisualFieldReading.NOT_FOUND,
                VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND));

        DocumentExtractionResult result = service.uploadAndExtract(command());

        ExtractedField<?> placeOfBirth = result.fieldCandidates().get(ApplicantProfile.PATH_PERSONAL_PLACE_OF_BIRTH);
        assertEquals(0.70, placeOfBirth.confidence());
        assertEquals(VerificationStatus.UNVERIFIED, placeOfBirth.verificationStatus());
    }

    @Test
    void uploadAndExtract_malformedVisualDate_producesNoCandidate() {
        stubHappyPathUpTo(VALID_LINE_2);
        when(visionExtractionPort.extract(any())).thenReturn(new VisualPassportData(
                VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND,
                new VisualFieldReading("not-a-date", true),
                VisualFieldReading.NOT_FOUND,
                VisualFieldReading.NOT_FOUND,
                VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND));

        DocumentExtractionResult result = service.uploadAndExtract(command());

        assertNull(result.fieldCandidates().get(ApplicantProfile.PATH_PASSPORT_ISSUE_DATE));
    }

    @Test
    void uploadAndExtract_ambiguousVisualValue_failsValidation_assignsLowConfidence() {
        stubHappyPathUpTo(VALID_LINE_2);
        // A date that parses fine but is implausible: issue date after the (empty) expiry comparison fails
        // plausibility because it's in the future.
        when(visionExtractionPort.extract(any())).thenReturn(new VisualPassportData(
                VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND,
                new VisualFieldReading(LocalDate.now().plusYears(1).toString(), true),
                VisualFieldReading.NOT_FOUND,
                VisualFieldReading.NOT_FOUND,
                VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND));

        DocumentExtractionResult result = service.uploadAndExtract(command());

        ExtractedField<?> issueDate = result.fieldCandidates().get(ApplicantProfile.PATH_PASSPORT_ISSUE_DATE);
        assertEquals(0.40, issueDate.confidence());
        assertEquals(VerificationStatus.UNVERIFIED, issueDate.verificationStatus());
    }

    @Test
    void uploadAndExtract_missingVisualField_producesNoCandidate() {
        stubHappyPathUpTo(VALID_LINE_2);
        when(visionExtractionPort.extract(any())).thenReturn(VisualPassportData.empty());

        DocumentExtractionResult result = service.uploadAndExtract(command());

        assertNull(result.fieldCandidates().get(ApplicantProfile.PATH_PASSPORT_ISSUE_DATE));
        assertNull(result.fieldCandidates().get(ApplicantProfile.PATH_PERSONAL_PLACE_OF_BIRTH));
        assertNull(result.fieldCandidates().get(ApplicantProfile.PATH_PASSPORT_TYPE));
    }

    // ── Cross-field validation ───────────────────────────────────────────────

    @Test
    void uploadAndExtract_mrzAndVisualAgreeOnPassportNumber_producesOneCandidateNoConflictKeys() {
        stubHappyPathUpTo(VALID_LINE_2);
        when(visionExtractionPort.extract(any())).thenReturn(new VisualPassportData(
                new VisualFieldReading("A1234567", true), VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND,
                VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND,
                VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND));

        DocumentExtractionResult result = service.uploadAndExtract(command());

        Map<String, ExtractedField<?>> candidates = result.fieldCandidates();
        assertEquals("A1234567", candidates.get(ApplicantProfile.PATH_PASSPORT_NUMBER).normalizedValue());
        assertEquals(0.95, candidates.get(ApplicantProfile.PATH_PASSPORT_NUMBER).confidence());
        assertFalse(candidates.containsKey(ApplicantProfile.PATH_PASSPORT_NUMBER + ".conflict.mrz"));
        assertFalse(candidates.containsKey(ApplicantProfile.PATH_PASSPORT_NUMBER + ".conflict.visual"));
    }

    @Test
    void uploadAndExtract_mrzAndVisualConflictOnPassportNumber_neverSilentlyChoosesOne() {
        stubHappyPathUpTo(VALID_LINE_2);
        when(visionExtractionPort.extract(any())).thenReturn(new VisualPassportData(
                new VisualFieldReading("A1234561", true), VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND,
                VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND,
                VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND));

        DocumentExtractionResult result = service.uploadAndExtract(command());

        Map<String, ExtractedField<?>> candidates = result.fieldCandidates();
        assertNull(candidates.get(ApplicantProfile.PATH_PASSPORT_NUMBER));

        ExtractedField<?> mrzSide = candidates.get(ApplicantProfile.PATH_PASSPORT_NUMBER + ".conflict.mrz");
        ExtractedField<?> visualSide = candidates.get(ApplicantProfile.PATH_PASSPORT_NUMBER + ".conflict.visual");
        assertEquals("A1234567", mrzSide.normalizedValue());
        assertEquals(VerificationStatus.UNVERIFIED, mrzSide.verificationStatus());
        assertEquals("A1234561", visualSide.normalizedValue());
        assertEquals(VerificationStatus.UNVERIFIED, visualSide.verificationStatus());
    }

    @Test
    void uploadAndExtract_mrzAndVisualConflictOnDateOfBirth_bothSidesSurfacedUnverified() {
        stubHappyPathUpTo(VALID_LINE_2);
        when(visionExtractionPort.extract(any())).thenReturn(new VisualPassportData(
                VisualFieldReading.NOT_FOUND, new VisualFieldReading("1991-05-15", true), VisualFieldReading.NOT_FOUND,
                VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND,
                VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND));

        DocumentExtractionResult result = service.uploadAndExtract(command());

        Map<String, ExtractedField<?>> candidates = result.fieldCandidates();
        assertNull(candidates.get(ApplicantProfile.PATH_PERSONAL_DATE_OF_BIRTH));
        assertEquals(LocalDate.of(1990, 5, 15),
                candidates.get(ApplicantProfile.PATH_PERSONAL_DATE_OF_BIRTH + ".conflict.mrz").normalizedValue());
        assertEquals(LocalDate.of(1991, 5, 15),
                candidates.get(ApplicantProfile.PATH_PERSONAL_DATE_OF_BIRTH + ".conflict.visual").normalizedValue());
    }

    // ── Image quality gate (unchanged behavior, still guards vision extraction) ─

    @Test
    void uploadAndExtract_poorImageQuality_rejectsBeforeInvokingOcrOrVision() {
        when(documentStoragePort.store(any(), any(), any())).thenReturn("storage/path");
        when(imageQualityPort.assess(any())).thenReturn(
                ImageQualityAssessment.rejected(Set.of(ImageQualityIssue.BLURRY, ImageQualityIssue.POOR_LIGHTING)));

        DocumentExtractionResult result = service.uploadAndExtract(command());

        assertEquals(DocumentProcessingStatus.QUALITY_REJECTED, result.status());
        assertTrue(result.fieldCandidates().isEmpty());
        assertEquals(Set.of(ImageQualityIssue.BLURRY, ImageQualityIssue.POOR_LIGHTING), result.issues());
        verifyNoInteractions(ocrPort);
        verifyNoInteractions(visionExtractionPort);
    }

    @Test
    void uploadAndExtract_mrzNotParsedFromOcrText_rejectsWithMrzNotReadableIssue() {
        when(documentStoragePort.store(any(), any(), any())).thenReturn("storage/path");
        when(imageQualityPort.assess(any())).thenReturn(ImageQualityAssessment.accepted());
        when(ocrPort.extractText(any())).thenReturn("no MRZ lines in this text at all");

        DocumentExtractionResult result = service.uploadAndExtract(command());

        assertEquals(DocumentProcessingStatus.QUALITY_REJECTED, result.status());
        assertEquals(Set.of(ImageQualityIssue.MRZ_NOT_READABLE), result.issues());
        verifyNoInteractions(visionExtractionPort);
    }
}
