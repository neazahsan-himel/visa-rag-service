package com.neaz.travel.visa.domain.model;

import com.neaz.travel.visa.common.exception.VisaRagException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for one applicant's reusable profile data. Fields are
 * stored generically, keyed by the canonical dot-paths declared as
 * constants on this class, so that verification workflows
 * ({@link #confirmField}, {@link #correctField}, {@link #rejectField})
 * operate uniformly on any field without a hand-written case per field.
 * Typed, ergonomic views ({@link #personalInfo()} etc.) are assembled from
 * the map on demand for callers that want a specific shape.
 *
 * <p>{@code purposeOfVisit} is the one exception: it is free text the
 * applicant types directly, with no source document and nothing to verify,
 * so it is stored as a plain field rather than in the generic map.
 */
public final class ApplicantProfile {

    public static final String PATH_PERSONAL_FULL_NAME = "personalInfo.fullName";
    public static final String PATH_PERSONAL_DATE_OF_BIRTH = "personalInfo.dateOfBirth";
    public static final String PATH_PERSONAL_GENDER = "personalInfo.gender";
    public static final String PATH_PERSONAL_PLACE_OF_BIRTH = "personalInfo.placeOfBirth";

    public static final String PATH_PASSPORT_NUMBER = "passportInfo.passportNumber";
    public static final String PATH_PASSPORT_ISSUING_COUNTRY = "passportInfo.issuingCountry";
    public static final String PATH_PASSPORT_ISSUE_DATE = "passportInfo.issueDate";
    public static final String PATH_PASSPORT_EXPIRY_DATE = "passportInfo.expiryDate";
    public static final String PATH_PASSPORT_TYPE = "passportInfo.passportType";

    public static final String PATH_NATIONALITY = "nationalityInfo.nationality";
    public static final String PATH_PREVIOUS_NATIONALITY = "nationalityInfo.previousNationality";

    public static final String PATH_PERMANENT_ADDRESS = "addressInfo.permanentAddress";
    public static final String PATH_CURRENT_ADDRESS = "addressInfo.currentAddress";

    public static final String PATH_FATHER_NAME = "familyInfo.fatherName";
    public static final String PATH_MOTHER_NAME = "familyInfo.motherName";
    public static final String PATH_SPOUSE_NAME = "familyInfo.spouseName";
    public static final String PATH_MARITAL_STATUS = "familyInfo.maritalStatus";

    public static final String PATH_OCCUPATION = "employmentInfo.occupation";
    public static final String PATH_EMPLOYER_NAME = "employmentInfo.employerName";
    public static final String PATH_EMPLOYER_ADDRESS = "employmentInfo.employerAddress";
    public static final String PATH_MONTHLY_INCOME = "employmentInfo.monthlyIncome";

    public static final String PATH_INTENDED_ARRIVAL_DATE = "travelInfo.intendedArrivalDate";
    public static final String PATH_INTENDED_DURATION_DAYS = "travelInfo.intendedDurationDays";
    public static final String PATH_PORT_OF_ENTRY = "travelInfo.portOfEntry";

    /**
     * Routing key for {@link #setPurposeOfVisit}, not a key in the generic
     * field map — {@code purposeOfVisit} is stored as a plain field (see
     * class Javadoc), but callers addressing fields generically by path
     * still need one constant to name it.
     */
    public static final String PATH_PURPOSE_OF_VISIT = "travelInfo.purposeOfVisit";

    private final UUID id;
    private final UUID applicantId;
    private final Instant createdAt;
    private final Map<String, ExtractedField<?>> fields;
    private Instant lastModifiedAt;
    private String purposeOfVisit;

    public ApplicantProfile(UUID id, UUID applicantId, Instant createdAt, Instant lastModifiedAt,
                             Map<String, ExtractedField<?>> fields, String purposeOfVisit) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.applicantId = Objects.requireNonNull(applicantId, "applicantId must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.lastModifiedAt = Objects.requireNonNull(lastModifiedAt, "lastModifiedAt must not be null");
        this.fields = new HashMap<>(Objects.requireNonNull(fields, "fields must not be null"));
        this.purposeOfVisit = purposeOfVisit;
    }

    /**
     * Sets or overwrites the value at {@code path}. The path is expected to
     * be one of this class's {@code PATH_*} constants; nothing enforces that
     * at compile time, so callers must use the constants rather than literal
     * strings.
     */
    public <T> void setField(String path, ExtractedField<T> value) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(value, "value must not be null");
        fields.put(path, value);
        lastModifiedAt = Instant.now();
    }

    public ExtractedField<?> getField(String path) {
        return fields.get(path);
    }

    /** Unmodifiable snapshot of every field currently set. For persistence/serialization use. */
    public Map<String, ExtractedField<?>> getFields() {
        return Map.copyOf(fields);
    }

    /**
     * Accepts the value at {@code path} unchanged.
     *
     * @throws VisaRagException if no value has been set at {@code path}
     */
    public void confirmField(String path) {
        ExtractedField<?> existing = requireField(path);
        fields.put(path, withStatus(existing, existing.normalizedValue(), VerificationStatus.USER_CONFIRMED));
        lastModifiedAt = Instant.now();
    }

    /**
     * Replaces the normalized value at {@code path} with a corrected one.
     * {@code extractedValue}, {@code confidence}, and {@code sourceDocumentId}
     * are preserved for audit.
     *
     * @throws VisaRagException if no value has been set at {@code path}
     */
    public <T> void correctField(String path, T correctedNormalizedValue) {
        Objects.requireNonNull(correctedNormalizedValue, "correctedNormalizedValue must not be null");
        ExtractedField<?> existing = requireField(path);
        fields.put(path, withStatus(existing, correctedNormalizedValue, VerificationStatus.USER_CORRECTED));
        lastModifiedAt = Instant.now();
    }

    /**
     * Marks the value at {@code path} as having no usable value.
     *
     * @throws VisaRagException if no value has been set at {@code path}
     */
    public void rejectField(String path) {
        ExtractedField<?> existing = requireField(path);
        fields.put(path, withStatus(existing, existing.normalizedValue(), VerificationStatus.REJECTED));
        lastModifiedAt = Instant.now();
    }

    private ExtractedField<?> requireField(String path) {
        ExtractedField<?> existing = fields.get(path);
        if (existing == null) {
            throw new VisaRagException("No value set for field '" + path + "'; cannot verify a field that hasn't been set");
        }
        return existing;
    }

    @SuppressWarnings("unchecked")
    private static <T> ExtractedField<T> withStatus(ExtractedField<?> existing, T normalizedValue, VerificationStatus status) {
        ExtractedField<T> typed = (ExtractedField<T>) existing;
        return new ExtractedField<>(typed.extractedValue(), normalizedValue, typed.confidence(),
                typed.sourceDocumentId(), status);
    }

    @SuppressWarnings("unchecked")
    private <T> ExtractedField<T> getTyped(String path) {
        return (ExtractedField<T>) fields.get(path);
    }

    public PersonalInfo personalInfo() {
        return new PersonalInfo(getTyped(PATH_PERSONAL_FULL_NAME), getTyped(PATH_PERSONAL_DATE_OF_BIRTH),
                getTyped(PATH_PERSONAL_GENDER), getTyped(PATH_PERSONAL_PLACE_OF_BIRTH));
    }

    public PassportInfo passportInfo() {
        return new PassportInfo(getTyped(PATH_PASSPORT_NUMBER), getTyped(PATH_PASSPORT_ISSUING_COUNTRY),
                getTyped(PATH_PASSPORT_ISSUE_DATE), getTyped(PATH_PASSPORT_EXPIRY_DATE), getTyped(PATH_PASSPORT_TYPE));
    }

    public NationalityInfo nationalityInfo() {
        return new NationalityInfo(getTyped(PATH_NATIONALITY), getTyped(PATH_PREVIOUS_NATIONALITY));
    }

    public AddressInfo addressInfo() {
        return new AddressInfo(getTyped(PATH_PERMANENT_ADDRESS), getTyped(PATH_CURRENT_ADDRESS));
    }

    public FamilyInfo familyInfo() {
        return new FamilyInfo(getTyped(PATH_FATHER_NAME), getTyped(PATH_MOTHER_NAME),
                getTyped(PATH_SPOUSE_NAME), getTyped(PATH_MARITAL_STATUS));
    }

    public EmploymentInfo employmentInfo() {
        return new EmploymentInfo(getTyped(PATH_OCCUPATION), getTyped(PATH_EMPLOYER_NAME),
                getTyped(PATH_EMPLOYER_ADDRESS), getTyped(PATH_MONTHLY_INCOME));
    }

    public TravelInfo travelInfo() {
        return new TravelInfo(purposeOfVisit, getTyped(PATH_INTENDED_ARRIVAL_DATE),
                getTyped(PATH_INTENDED_DURATION_DAYS), getTyped(PATH_PORT_OF_ENTRY));
    }

    public void setPurposeOfVisit(String purposeOfVisit) {
        this.purposeOfVisit = purposeOfVisit;
        this.lastModifiedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicantId() {
        return applicantId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastModifiedAt() {
        return lastModifiedAt;
    }

    public String getPurposeOfVisit() {
        return purposeOfVisit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApplicantProfile other)) {
            return false;
        }
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "ApplicantProfile{id=%s, applicantId=%s, fields=%d}".formatted(id, applicantId, fields.size());
    }
}
