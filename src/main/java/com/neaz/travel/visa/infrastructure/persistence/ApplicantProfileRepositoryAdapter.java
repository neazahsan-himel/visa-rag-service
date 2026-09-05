package com.neaz.travel.visa.infrastructure.persistence;

import com.neaz.travel.visa.domain.model.Address;
import com.neaz.travel.visa.domain.model.ApplicantProfile;
import com.neaz.travel.visa.domain.model.ExtractedField;
import com.neaz.travel.visa.domain.model.VerificationStatus;
import com.neaz.travel.visa.domain.repository.ApplicantProfileRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ApplicantProfileRepository} implementation backed by PostgreSQL via
 * Spring Data JPA. {@code ApplicantProfile}'s generic field map is persisted
 * as one {@link ExtractedFieldJpaEntity} row per entry, replacing all rows
 * for the profile on every save rather than diffing individual changes —
 * simple and correct given Phase B's small, fixed field count, at the cost
 * of every row (including unchanged ones) getting a fresh id and {@code
 * created_at} on each save. Nothing in Phase B relies on those columns
 * reflecting true original creation, so this is accepted rather than built
 * out into a real upsert.
 *
 * <p>{@link Address} values are serialized as a Unit Separator
 * ({@code U+001F})-delimited string rather than JSON, since it is a fixed
 * five-field composite and a real address could never contain a control
 * character.
 */
@Repository
public class ApplicantProfileRepositoryAdapter implements ApplicantProfileRepository {

    private static final String ADDRESS_DELIMITER = String.valueOf((char) 0x1F);

    private final ApplicantProfileJpaRepository applicantProfileJpaRepository;
    private final ExtractedFieldJpaRepository extractedFieldJpaRepository;

    public ApplicantProfileRepositoryAdapter(ApplicantProfileJpaRepository applicantProfileJpaRepository,
                                              ExtractedFieldJpaRepository extractedFieldJpaRepository) {
        this.applicantProfileJpaRepository = applicantProfileJpaRepository;
        this.extractedFieldJpaRepository = extractedFieldJpaRepository;
    }

    @Override
    @Transactional
    public ApplicantProfile save(ApplicantProfile profile) {
        ApplicantProfileJpaEntity entity = new ApplicantProfileJpaEntity(
                profile.getId(), profile.getApplicantId(), profile.getPurposeOfVisit(),
                profile.getCreatedAt(), profile.getLastModifiedAt());
        applicantProfileJpaRepository.save(entity);

        extractedFieldJpaRepository.deleteByApplicantProfileId(profile.getId());
        List<ExtractedFieldJpaEntity> fieldEntities = profile.getFields().entrySet().stream()
                .map(fieldEntry -> toEntity(profile.getId(), fieldEntry.getKey(), fieldEntry.getValue()))
                .toList();
        extractedFieldJpaRepository.saveAll(fieldEntities);

        return profile;
    }

    @Override
    public Optional<ApplicantProfile> findById(UUID id) {
        return applicantProfileJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<ApplicantProfile> findByApplicantId(UUID applicantId) {
        return applicantProfileJpaRepository.findByApplicantId(applicantId).map(this::toDomain);
    }

    private ApplicantProfile toDomain(ApplicantProfileJpaEntity entity) {
        Map<String, ExtractedField<?>> fields = new HashMap<>();
        for (ExtractedFieldJpaEntity fieldEntity : extractedFieldJpaRepository.findByApplicantProfileId(entity.getId())) {
            fields.put(fieldEntity.getFieldPath(), toExtractedField(fieldEntity));
        }
        return new ApplicantProfile(entity.getId(), entity.getApplicantId(), entity.getCreatedAt(),
                entity.getLastModifiedAt(), fields, entity.getPurposeOfVisit());
    }

    private ExtractedFieldJpaEntity toEntity(UUID applicantProfileId, String fieldPath, ExtractedField<?> field) {
        return new ExtractedFieldJpaEntity(
                UUID.randomUUID(),
                applicantProfileId,
                field.sourceDocumentId(),
                fieldPath,
                serialize(fieldPath, field.extractedValue()),
                serialize(fieldPath, field.normalizedValue()),
                BigDecimal.valueOf(field.confidence()).setScale(3, RoundingMode.HALF_UP),
                field.verificationStatus().name(),
                Instant.now());
    }

    private ExtractedField<?> toExtractedField(ExtractedFieldJpaEntity entity) {
        String path = entity.getFieldPath();
        return new ExtractedField<>(
                deserialize(path, entity.getExtractedValue()),
                deserialize(path, entity.getNormalizedValue()),
                entity.getConfidence().doubleValue(),
                entity.getSourceDocumentId(),
                VerificationStatus.valueOf(entity.getVerificationStatus()));
    }

    private String serialize(String fieldPath, Object value) {
        if (isAddressPath(fieldPath)) {
            Address address = (Address) value;
            return String.join(ADDRESS_DELIMITER,
                    nullToEmpty(address.line1()), nullToEmpty(address.line2()), nullToEmpty(address.city()),
                    nullToEmpty(address.postalCode()), nullToEmpty(address.countryIso()));
        }
        return value.toString();
    }

    private Object deserialize(String fieldPath, String stored) {
        if (isAddressPath(fieldPath)) {
            String[] parts = stored.split(ADDRESS_DELIMITER, -1);
            return new Address(emptyToNull(parts[0]), emptyToNull(parts[1]), emptyToNull(parts[2]),
                    emptyToNull(parts[3]), emptyToNull(parts[4]));
        }
        if (isDatePath(fieldPath)) {
            return LocalDate.parse(stored);
        }
        if (ApplicantProfile.PATH_INTENDED_DURATION_DAYS.equals(fieldPath)) {
            return Integer.valueOf(stored);
        }
        return stored;
    }

    private boolean isAddressPath(String fieldPath) {
        return ApplicantProfile.PATH_PERMANENT_ADDRESS.equals(fieldPath)
                || ApplicantProfile.PATH_CURRENT_ADDRESS.equals(fieldPath);
    }

    private boolean isDatePath(String fieldPath) {
        return ApplicantProfile.PATH_PERSONAL_DATE_OF_BIRTH.equals(fieldPath)
                || ApplicantProfile.PATH_PASSPORT_ISSUE_DATE.equals(fieldPath)
                || ApplicantProfile.PATH_PASSPORT_EXPIRY_DATE.equals(fieldPath)
                || ApplicantProfile.PATH_INTENDED_ARRIVAL_DATE.equals(fieldPath);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String emptyToNull(String s) {
        return s.isEmpty() ? null : s;
    }
}
