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
import com.neaz.travel.visa.domain.model.ExtractedField;
import com.neaz.travel.visa.domain.model.ImageQualityAssessment;
import com.neaz.travel.visa.domain.model.MrzData;
import com.neaz.travel.visa.domain.model.VerificationStatus;
import com.neaz.travel.visa.domain.model.VisualFieldReading;
import com.neaz.travel.visa.domain.model.VisualPassportData;
import com.neaz.travel.visa.domain.repository.ApplicantDocumentRepository;
import com.neaz.travel.visa.domain.service.MrzParser;
import com.neaz.travel.visa.domain.service.PassportFieldValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the passport-upload-to-candidates pipeline: store the image,
 * run the pre-OCR quality gate, then read fields from two independent
 * sources — MRZ OCR (checksum-validated) and multimodal vision extraction
 * (validated, qualitatively self-reported) — cross-check the fields both
 * sources supply, and build field candidates for the applicant to review.
 * Contains no I/O, image-analysis, OCR, or vision-model logic itself — that
 * lives entirely behind the application ports it depends on. {@link
 * MrzParser} is instantiated directly (stateless, zero-dependency pure-Java
 * algorithm), the same precedent as {@code VisaQueryService}'s direct use of
 * {@code ConfidenceScorer}.
 *
 * <p>Nothing this service returns is persisted into an {@link ApplicantProfile}
 * — every candidate carries {@link VerificationStatus#UNVERIFIED} and is only
 * written to a profile by a separate confirmation step, once the applicant
 * has reviewed it.
 *
 * <p><b>Confidence values below are heuristic/provisional extraction-signal
 * strengths, not statistically calibrated probabilities.</b> {@code 0.95}
 * means "the MRZ check digit for this value matched" — not "95% likely
 * correct". See the per-constant Javadoc for what each value actually
 * signals.
 */
@Service
public class DocumentIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIntelligenceService.class);

    private static final String QUALITY_REJECTION_MESSAGE =
            "Please upload a clear, flat, well-lit passport scan/photo.";

    /**
     * MRZ check digit(s) relevant to this field matched. A strong MRZ
     * checksum-validation signal — not a 95% probability the field is
     * correct.
     */
    private static final double MRZ_CHECKSUM_VALID_CONFIDENCE = 0.95;

    /**
     * MRZ check digit(s) relevant to this field did not match. A weak MRZ
     * checksum-validation signal — not a 40% probability the field is
     * correct.
     */
    private static final double MRZ_CHECKSUM_INVALID_CONFIDENCE = 0.40;

    /**
     * The vision model reported this field's label and value as clearly
     * legible, and the value passed format/plausibility validation. A
     * strong visual-extraction signal — not a 90% probability the field is
     * correct.
     */
    private static final double VISUAL_HIGH_CONFIDENCE = 0.90;

    /**
     * The value passed format/plausibility validation, but the vision model
     * did not report it as clearly legible (partial ambiguity or imperfect
     * readability). A moderate visual-extraction signal.
     */
    private static final double VISUAL_MEDIUM_CONFIDENCE = 0.70;

    /**
     * The value failed format/plausibility validation. A weak
     * visual-extraction signal, kept as a candidate (rather than omitted)
     * only because the architecture allows representing uncertain
     * candidates at low confidence for applicant review.
     */
    private static final double VISUAL_LOW_CONFIDENCE = 0.40;

    /**
     * Key suffix marking the MRZ side of a field where MRZ and visual
     * extraction disagree. Neither reading is written under the plain
     * field path in that case — see {@link #mergeWithCrossValidation}.
     */
    private static final String CONFLICT_SUFFIX_MRZ = ".conflict.mrz";

    /** Key suffix marking the visual side of a disagreeing field; see {@link #CONFLICT_SUFFIX_MRZ}. */
    private static final String CONFLICT_SUFFIX_VISUAL = ".conflict.visual";

    private final ImageQualityPort imageQualityPort;
    private final OcrPort ocrPort;
    private final VisionExtractionPort visionExtractionPort;
    private final DocumentStoragePort documentStoragePort;
    private final ApplicantDocumentRepository applicantDocumentRepository;
    private final MrzParser mrzParser = new MrzParser();

    public DocumentIntelligenceService(
            ImageQualityPort imageQualityPort,
            OcrPort ocrPort,
            VisionExtractionPort visionExtractionPort,
            DocumentStoragePort documentStoragePort,
            ApplicantDocumentRepository applicantDocumentRepository) {
        this.imageQualityPort = imageQualityPort;
        this.ocrPort = ocrPort;
        this.visionExtractionPort = visionExtractionPort;
        this.documentStoragePort = documentStoragePort;
        this.applicantDocumentRepository = applicantDocumentRepository;
    }

    public DocumentExtractionResult uploadAndExtract(UploadApplicantDocumentCommand command) {
        UUID documentId = UUID.randomUUID();
        String storagePath = documentStoragePort.store(command.applicantId(), documentId, command.imageBytes());
        ApplicantDocument document = new ApplicantDocument(documentId, command.applicantId(), command.documentType(),
                storagePath, Instant.now(), DocumentProcessingStatus.UPLOADED);
        applicantDocumentRepository.save(document);

        ImageQualityAssessment assessment = imageQualityPort.assess(command.imageBytes());
        if (!assessment.acceptable()) {
            log.info("Document {} rejected by image-quality gate: {}", documentId, assessment.issues());
            return rejectForQuality(document);
        }

        String ocrText = ocrPort.extractText(command.imageBytes());
        Optional<MrzData> mrzData = mrzParser.parse(ocrText);
        if (mrzData.isEmpty()) {
            log.info("Document {} passed image-quality gate but no MRZ could be parsed from OCR text", documentId);
            return rejectForQuality(document);
        }

        Map<String, ExtractedField<?>> mrzCandidates = buildMrzFieldCandidates(documentId, mrzData.get());
        VisualPassportData visualData = visionExtractionPort.extract(command.imageBytes());
        Map<String, ExtractedField<?>> visualCandidates = buildVisualFieldCandidates(documentId, visualData);
        Map<String, ExtractedField<?>> candidates = mergeWithCrossValidation(mrzCandidates, visualCandidates);

        document.markExtracted();
        applicantDocumentRepository.save(document);
        return DocumentExtractionResult.extracted(documentId, candidates);
    }

    private DocumentExtractionResult rejectForQuality(ApplicantDocument document) {
        document.rejectForQuality();
        applicantDocumentRepository.save(document);
        return DocumentExtractionResult.qualityRejected(document.getId(), QUALITY_REJECTION_MESSAGE);
    }

    /**
     * Maps MRZ fields onto {@code ApplicantProfile} paths. Passport number,
     * date of birth, and expiry date each have their own MRZ check digit, so
     * their confidence reflects that specific check. Name, issuing country,
     * nationality, and sex have no dedicated check digit in the MRZ, so their
     * confidence borrows the composite check digit as the best available
     * read-quality signal. Fields the MRZ doesn't carry at all (issue date,
     * place of birth, passport type) are left unset here — see {@link
     * #buildVisualFieldCandidates}.
     */
    private Map<String, ExtractedField<?>> buildMrzFieldCandidates(UUID documentId, MrzData mrz) {
        Map<String, ExtractedField<?>> candidates = new HashMap<>();
        double compositeConfidence = mrz.compositeCheckValid() ? MRZ_CHECKSUM_VALID_CONFIDENCE : MRZ_CHECKSUM_INVALID_CONFIDENCE;

        String fullName = (mrz.givenNames() + " " + mrz.surname()).trim();
        putIfPresent(candidates, ApplicantProfile.PATH_PERSONAL_FULL_NAME, fullName.isBlank() ? null : fullName,
                documentId, compositeConfidence);
        putIfPresent(candidates, ApplicantProfile.PATH_PERSONAL_GENDER, mrz.sex(), documentId, compositeConfidence);
        putIfPresent(candidates, ApplicantProfile.PATH_PASSPORT_ISSUING_COUNTRY, blankToNull(mrz.issuingCountry()),
                documentId, compositeConfidence);
        putIfPresent(candidates, ApplicantProfile.PATH_NATIONALITY, blankToNull(mrz.nationality()), documentId,
                compositeConfidence);

        putIfPresent(candidates, ApplicantProfile.PATH_PASSPORT_NUMBER, blankToNull(mrz.passportNumber()), documentId,
                mrz.passportNumberCheckValid() ? MRZ_CHECKSUM_VALID_CONFIDENCE : MRZ_CHECKSUM_INVALID_CONFIDENCE);
        putIfPresent(candidates, ApplicantProfile.PATH_PERSONAL_DATE_OF_BIRTH, mrz.dateOfBirth(), documentId,
                mrz.dateOfBirthCheckValid() ? MRZ_CHECKSUM_VALID_CONFIDENCE : MRZ_CHECKSUM_INVALID_CONFIDENCE);
        putIfPresent(candidates, ApplicantProfile.PATH_PASSPORT_EXPIRY_DATE, mrz.expirationDate(), documentId,
                mrz.expirationDateCheckValid() ? MRZ_CHECKSUM_VALID_CONFIDENCE : MRZ_CHECKSUM_INVALID_CONFIDENCE);

        return candidates;
    }

    /**
     * Maps validated visual-page readings onto {@code ApplicantProfile}
     * paths. {@code issueDate}/{@code placeOfBirth}/{@code passportType}
     * have no MRZ equivalent and land here as the applicant's only
     * candidate for those paths. {@code passportNumber}/{@code dateOfBirth}/
     * {@code expiryDate} are also read here purely so {@link
     * #mergeWithCrossValidation} can compare them against the MRZ reading —
     * a value not found on the page produces no candidate at all (never a
     * guess), and a value that cannot even be parsed into its target type is
     * likewise omitted rather than represented with a made-up value.
     */
    private Map<String, ExtractedField<?>> buildVisualFieldCandidates(UUID documentId, VisualPassportData visual) {
        Map<String, ExtractedField<?>> candidates = new HashMap<>();

        LocalDate dateOfBirth = parseIsoDate(visual.dateOfBirth().rawValue());
        LocalDate expiryDate = parseIsoDate(visual.expiryDate().rawValue());
        LocalDate issueDate = parseIsoDate(visual.issueDate().rawValue());

        putVisualDateCandidate(candidates, ApplicantProfile.PATH_PERSONAL_DATE_OF_BIRTH, visual.dateOfBirth(),
                dateOfBirth, documentId, PassportFieldValidator.isPlausibleDateOfBirth(dateOfBirth));
        putVisualDateCandidate(candidates, ApplicantProfile.PATH_PASSPORT_EXPIRY_DATE, visual.expiryDate(),
                expiryDate, documentId, PassportFieldValidator.isPlausibleExpiryDate(expiryDate, issueDate));
        putVisualDateCandidate(candidates, ApplicantProfile.PATH_PASSPORT_ISSUE_DATE, visual.issueDate(),
                issueDate, documentId, PassportFieldValidator.isPlausibleIssueDate(issueDate, expiryDate));

        putVisualStringCandidate(candidates, ApplicantProfile.PATH_PASSPORT_NUMBER, visual.passportNumber(),
                documentId, PassportFieldValidator.isValidPassportNumber(visual.passportNumber().rawValue()));
        putVisualStringCandidate(candidates, ApplicantProfile.PATH_PERSONAL_PLACE_OF_BIRTH, visual.placeOfBirth(),
                documentId, PassportFieldValidator.isValidPlaceOfBirth(visual.placeOfBirth().rawValue()));
        putVisualStringCandidate(candidates, ApplicantProfile.PATH_PASSPORT_TYPE, visual.passportType(),
                documentId, PassportFieldValidator.isValidPassportType(visual.passportType().rawValue()));

        putVisualFullNameCandidate(candidates, visual.givenNames(), visual.surname(), documentId);

        return candidates;
    }

    /**
     * Combines {@code surname}/{@code givenNames} into a single full-name
     * candidate the same way {@link #buildMrzFieldCandidates} combines the
     * MRZ's own surname/given-name fields, so the two are directly
     * comparable by {@link #mergeWithCrossValidation}. Only produces a
     * candidate when both parts were found — a name built from just one part
     * would not be comparable to the MRZ's full name and risks a spurious
     * conflict.
     */
    private static void putVisualFullNameCandidate(Map<String, ExtractedField<?>> candidates,
            VisualFieldReading givenNames, VisualFieldReading surname, UUID documentId) {
        if (!givenNames.isPresent() || !surname.isPresent()) {
            return;
        }
        String fullName = (givenNames.rawValue() + " " + surname.rawValue()).trim();
        double confidence = visualConfidence(PassportFieldValidator.isValidFullName(fullName),
                givenNames.clearlyReadable() && surname.clearlyReadable());
        candidates.put(ApplicantProfile.PATH_PERSONAL_FULL_NAME,
                new ExtractedField<>(fullName, fullName, confidence, documentId, VerificationStatus.UNVERIFIED));
    }

    /**
     * Reconciles MRZ and visual candidates for the fields both sources can
     * supply. A path only one source found is passed through unchanged. When
     * both sources found it and agree, the MRZ candidate is kept (its
     * confidence is checksum-backed, a stronger signal than the visual
     * heuristic) and the visual reading is discarded as redundant. When they
     * disagree, neither is written under the plain path — this is the
     * "never silently choose one" rule from the target architecture — and
     * both are exposed under {@link #CONFLICT_SUFFIX_MRZ}/{@link
     * #CONFLICT_SUFFIX_VISUAL} keys instead, still {@code UNVERIFIED}, for
     * the applicant to resolve.
     */
    private Map<String, ExtractedField<?>> mergeWithCrossValidation(
            Map<String, ExtractedField<?>> mrzCandidates, Map<String, ExtractedField<?>> visualCandidates) {
        Map<String, ExtractedField<?>> merged = new HashMap<>(mrzCandidates);
        for (Map.Entry<String, ExtractedField<?>> entry : visualCandidates.entrySet()) {
            String path = entry.getKey();
            ExtractedField<?> visualField = entry.getValue();
            ExtractedField<?> mrzField = mrzCandidates.get(path);

            if (mrzField == null) {
                merged.put(path, visualField);
            } else if (!valuesAgree(mrzField.normalizedValue(), visualField.normalizedValue())) {
                merged.remove(path);
                merged.put(path + CONFLICT_SUFFIX_MRZ, mrzField);
                merged.put(path + CONFLICT_SUFFIX_VISUAL, visualField);
            }
            // else: sources agree; the MRZ candidate already in `merged` stands.
        }
        return merged;
    }

    private static boolean valuesAgree(Object mrzValue, Object visualValue) {
        if (mrzValue instanceof String mrzString && visualValue instanceof String visualString) {
            return normalizeWhitespace(mrzString).equalsIgnoreCase(normalizeWhitespace(visualString));
        }
        return Objects.equals(mrzValue, visualValue);
    }

    /**
     * Collapses runs of internal whitespace to a single space and trims the
     * ends. MRZ-derived strings are already single-spaced (see {@code
     * MrzParser#cleanNamePart}), but a vision model's free-text reading of
     * the printed page is not guaranteed to be — without this, a name that
     * matches except for double-spacing would be reported as a conflict.
     */
    private static String normalizeWhitespace(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private static LocalDate parseIsoDate(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        try {
            return LocalDate.parse(rawValue.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static void putVisualDateCandidate(Map<String, ExtractedField<?>> candidates, String path,
            VisualFieldReading reading, LocalDate parsedValue, UUID documentId, boolean plausible) {
        if (!reading.isPresent() || parsedValue == null) {
            return;
        }
        double confidence = visualConfidence(plausible, reading.clearlyReadable());
        candidates.put(path, new ExtractedField<>(parsedValue, parsedValue, confidence, documentId, VerificationStatus.UNVERIFIED));
    }

    private static void putVisualStringCandidate(Map<String, ExtractedField<?>> candidates, String path,
            VisualFieldReading reading, UUID documentId, boolean valid) {
        if (!reading.isPresent()) {
            return;
        }
        double confidence = visualConfidence(valid, reading.clearlyReadable());
        candidates.put(path,
                new ExtractedField<>(reading.rawValue(), reading.rawValue(), confidence, documentId, VerificationStatus.UNVERIFIED));
    }

    private static double visualConfidence(boolean passesValidation, boolean clearlyReadable) {
        if (!passesValidation) {
            return VISUAL_LOW_CONFIDENCE;
        }
        return clearlyReadable ? VISUAL_HIGH_CONFIDENCE : VISUAL_MEDIUM_CONFIDENCE;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static <T> void putIfPresent(Map<String, ExtractedField<?>> candidates, String path, T value,
                                          UUID documentId, double confidence) {
        if (value == null) {
            return;
        }
        candidates.put(path, new ExtractedField<>(value, value, confidence, documentId, VerificationStatus.UNVERIFIED));
    }
}
