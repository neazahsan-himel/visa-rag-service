package com.neaz.travel.visa.application.document;

import com.neaz.travel.visa.application.applicant.ApplicantFieldValueParser;
import com.neaz.travel.visa.application.command.ConfirmExtractedFieldsCommand;
import com.neaz.travel.visa.application.result.ApplicantProfileResult;
import com.neaz.travel.visa.common.exception.ApplicantDocumentNotFoundException;
import com.neaz.travel.visa.domain.model.ApplicantDocument;
import com.neaz.travel.visa.domain.model.ApplicantProfile;
import com.neaz.travel.visa.domain.model.ExtractedField;
import com.neaz.travel.visa.domain.model.VerificationStatus;
import com.neaz.travel.visa.domain.repository.ApplicantDocumentRepository;
import com.neaz.travel.visa.domain.repository.ApplicantProfileRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the "applicant confirms extracted document fields" use case:
 * loads the source {@code ApplicantDocument}, writes each accepted candidate
 * into the applicant's profile as {@code USER_CONFIRMED}, and marks the
 * document confirmed. This is the only path by which a document-derived
 * value ever reaches an {@link ApplicantProfile} —
 * {@code DocumentIntelligenceService} never persists candidates itself.
 */
@Service
public class ExtractedFieldConfirmationService {

    private final ApplicantDocumentRepository applicantDocumentRepository;
    private final ApplicantProfileRepository applicantProfileRepository;

    public ExtractedFieldConfirmationService(
            ApplicantDocumentRepository applicantDocumentRepository,
            ApplicantProfileRepository applicantProfileRepository) {
        this.applicantDocumentRepository = applicantDocumentRepository;
        this.applicantProfileRepository = applicantProfileRepository;
    }

    public ApplicantProfileResult confirm(ConfirmExtractedFieldsCommand command) {
        ApplicantDocument document = applicantDocumentRepository.findById(command.documentId())
                .filter(candidate -> candidate.getApplicantId().equals(command.applicantId()))
                .orElseThrow(() -> new ApplicantDocumentNotFoundException(command.documentId()));

        ApplicantProfile profile = applicantProfileRepository.findByApplicantId(command.applicantId())
                .orElseGet(() -> newProfile(command.applicantId()));

        for (Map.Entry<String, Object> entry : command.fields().entrySet()) {
            Object typedValue = ApplicantFieldValueParser.parseTypedValue(entry.getKey(), entry.getValue());
            profile.setField(entry.getKey(),
                    new ExtractedField<>(typedValue, typedValue, 1.0, command.documentId(), VerificationStatus.USER_CONFIRMED));
        }

        document.confirm();
        applicantDocumentRepository.save(document);

        return ApplicantProfileResult.from(applicantProfileRepository.save(profile));
    }

    private ApplicantProfile newProfile(UUID applicantId) {
        Instant now = Instant.now();
        return new ApplicantProfile(UUID.randomUUID(), applicantId, now, now, Map.of(), null);
    }
}
