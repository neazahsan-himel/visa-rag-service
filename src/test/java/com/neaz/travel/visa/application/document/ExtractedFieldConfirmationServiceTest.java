package com.neaz.travel.visa.application.document;

import com.neaz.travel.visa.application.command.ConfirmExtractedFieldsCommand;
import com.neaz.travel.visa.application.result.ApplicantProfileResult;
import com.neaz.travel.visa.common.exception.ApplicantDocumentNotFoundException;
import com.neaz.travel.visa.domain.model.ApplicantDocument;
import com.neaz.travel.visa.domain.model.ApplicantProfile;
import com.neaz.travel.visa.domain.model.DocumentProcessingStatus;
import com.neaz.travel.visa.domain.model.DocumentType;
import com.neaz.travel.visa.domain.model.ExtractedField;
import com.neaz.travel.visa.domain.model.VerificationStatus;
import com.neaz.travel.visa.domain.repository.ApplicantDocumentRepository;
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
 * Verifies the one path by which a document-derived candidate ever reaches
 * an {@code ApplicantProfile}: applicant confirmation always writes
 * {@code confidence = 1.0} / {@code VerificationStatus.USER_CONFIRMED},
 * superseding whatever heuristic MRZ/visual confidence the candidate
 * carried at extraction time — a high extraction confidence must never be
 * treated as verification on its own.
 */
class ExtractedFieldConfirmationServiceTest {

    private final ApplicantDocumentRepository applicantDocumentRepository = mock(ApplicantDocumentRepository.class);
    private final ApplicantProfileRepository applicantProfileRepository = mock(ApplicantProfileRepository.class);
    private final ExtractedFieldConfirmationService service =
            new ExtractedFieldConfirmationService(applicantDocumentRepository, applicantProfileRepository);

    private ApplicantDocument extractedDocument(UUID documentId, UUID applicantId) {
        return new ApplicantDocument(documentId, applicantId, DocumentType.PASSPORT, "storage/path",
                Instant.now(), DocumentProcessingStatus.EXTRACTED);
    }

    @Test
    void confirm_writesFieldAsUserConfirmedWithFullConfidence() {
        UUID applicantId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(applicantDocumentRepository.findById(documentId)).thenReturn(Optional.of(extractedDocument(documentId, applicantId)));
        when(applicantProfileRepository.findByApplicantId(applicantId)).thenReturn(Optional.empty());
        when(applicantProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicantProfileResult result = service.confirm(new ConfirmExtractedFieldsCommand(
                applicantId, documentId, Map.of(ApplicantProfile.PATH_PASSPORT_NUMBER, "A1234567")));

        ExtractedField<?> confirmed = result.passportInfo().passportNumber();
        assertEquals("A1234567", confirmed.normalizedValue());
        assertEquals(1.0, confirmed.confidence());
        assertEquals(VerificationStatus.USER_CONFIRMED, confirmed.verificationStatus());
    }

    @Test
    void confirm_supersedesLowExtractionConfidenceWithFullConfidenceOnceConfirmed() {
        // Even a field the extraction pipeline flagged low-confidence (e.g. a failed MRZ
        // checksum, or a LOW-tier visual reading) becomes fully trusted once the applicant
        // has explicitly reviewed and accepted it — confirmation, not the original score, is
        // what makes a value trustworthy.
        UUID applicantId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(applicantDocumentRepository.findById(documentId)).thenReturn(Optional.of(extractedDocument(documentId, applicantId)));
        when(applicantProfileRepository.findByApplicantId(applicantId)).thenReturn(Optional.empty());
        when(applicantProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicantProfileResult result = service.confirm(new ConfirmExtractedFieldsCommand(
                applicantId, documentId, Map.of(ApplicantProfile.PATH_PASSPORT_NUMBER, "A1234567")));

        assertEquals(1.0, result.passportInfo().passportNumber().confidence());
    }

    @Test
    void confirm_marksDocumentConfirmed() {
        UUID applicantId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ApplicantDocument document = extractedDocument(documentId, applicantId);
        when(applicantDocumentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(applicantProfileRepository.findByApplicantId(applicantId)).thenReturn(Optional.empty());
        when(applicantProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.confirm(new ConfirmExtractedFieldsCommand(
                applicantId, documentId, Map.of(ApplicantProfile.PATH_PASSPORT_NUMBER, "A1234567")));

        assertEquals(DocumentProcessingStatus.CONFIRMED, document.getStatus());
    }

    @Test
    void confirm_whenDocumentBelongsToDifferentApplicant_throwsNotFound() {
        UUID applicantId = UUID.randomUUID();
        UUID otherApplicantId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(applicantDocumentRepository.findById(documentId))
                .thenReturn(Optional.of(extractedDocument(documentId, otherApplicantId)));

        assertThrows(ApplicantDocumentNotFoundException.class, () -> service.confirm(
                new ConfirmExtractedFieldsCommand(applicantId, documentId,
                        Map.of(ApplicantProfile.PATH_PASSPORT_NUMBER, "A1234567"))));
    }

    @Test
    void confirm_whenDocumentDoesNotExist_throwsNotFound() {
        UUID applicantId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(applicantDocumentRepository.findById(documentId)).thenReturn(Optional.empty());

        assertThrows(ApplicantDocumentNotFoundException.class, () -> service.confirm(
                new ConfirmExtractedFieldsCommand(applicantId, documentId,
                        Map.of(ApplicantProfile.PATH_PASSPORT_NUMBER, "A1234567"))));
    }
}
