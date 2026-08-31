package com.neaz.travel.visa.domain.model;

import com.neaz.travel.visa.common.exception.VisaRagException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link ApplicantProfile}'s generic field map: setting/reading a
 * field, assembling the typed views (including a non-{@code String} type,
 * {@link Address}, to prove the unchecked-cast machinery works), the
 * confirm/correct/reject verification transitions, and the exception thrown
 * when verifying a field that was never set.
 */
class ApplicantProfileTest {

    private ApplicantProfile newProfile() {
        Instant now = Instant.now();
        return new ApplicantProfile(UUID.randomUUID(), UUID.randomUUID(), now, now, Map.of(), null);
    }

    @Test
    void setField_thenPersonalInfo_returnsThatFieldPopulated() {
        ApplicantProfile profile = newProfile();

        profile.setField(ApplicantProfile.PATH_PERSONAL_FULL_NAME, ExtractedField.selfDeclared("Jane Doe"));

        assertEquals("Jane Doe", profile.personalInfo().fullName().normalizedValue());
    }

    @Test
    void personalInfo_withNoFieldsSet_returnsAllNullComponents() {
        ApplicantProfile profile = newProfile();

        PersonalInfo info = profile.personalInfo();

        assertNull(info.fullName());
        assertNull(info.dateOfBirth());
        assertNull(info.gender());
        assertNull(info.placeOfBirth());
    }

    @Test
    void addressInfo_withPermanentAddressSet_returnsAddressValue() {
        ApplicantProfile profile = newProfile();
        Address address = new Address("House 1", null, "Dhaka", "1200", "BD");

        profile.setField(ApplicantProfile.PATH_PERMANENT_ADDRESS, ExtractedField.selfDeclared(address));

        assertEquals(address, profile.addressInfo().permanentAddress().normalizedValue());
    }

    @Test
    void confirmField_onUnverifiedField_setsVerificationStatusToUserConfirmed() {
        ApplicantProfile profile = newProfile();
        ExtractedField<String> unverified = new ExtractedField<>(
                "Jane", "Jane", 0.7, UUID.randomUUID(), VerificationStatus.UNVERIFIED);
        profile.setField(ApplicantProfile.PATH_PERSONAL_FULL_NAME, unverified);

        profile.confirmField(ApplicantProfile.PATH_PERSONAL_FULL_NAME);

        assertEquals(VerificationStatus.USER_CONFIRMED, profile.personalInfo().fullName().verificationStatus());
    }

    @Test
    void confirmField_whenFieldNotSet_throwsVisaRagException() {
        ApplicantProfile profile = newProfile();

        assertThrows(VisaRagException.class, () -> profile.confirmField(ApplicantProfile.PATH_PERSONAL_FULL_NAME));
    }

    @Test
    void correctField_replacesNormalizedValueAndSetsUserCorrected_preservesExtractedValueAndSourceDocumentId() {
        ApplicantProfile profile = newProfile();
        UUID sourceDocumentId = UUID.randomUUID();
        ExtractedField<String> original = new ExtractedField<>(
                "Jhon Doe", "Jhon Doe", 0.8, sourceDocumentId, VerificationStatus.UNVERIFIED);
        profile.setField(ApplicantProfile.PATH_PERSONAL_FULL_NAME, original);

        profile.correctField(ApplicantProfile.PATH_PERSONAL_FULL_NAME, "John Doe");

        ExtractedField<String> corrected = profile.personalInfo().fullName();
        assertEquals("Jhon Doe", corrected.extractedValue());
        assertEquals("John Doe", corrected.normalizedValue());
        assertEquals(sourceDocumentId, corrected.sourceDocumentId());
        assertEquals(VerificationStatus.USER_CORRECTED, corrected.verificationStatus());
    }

    @Test
    void correctField_whenFieldNotSet_throwsVisaRagException() {
        ApplicantProfile profile = newProfile();

        assertThrows(VisaRagException.class,
                () -> profile.correctField(ApplicantProfile.PATH_PERSONAL_FULL_NAME, "John Doe"));
    }

    @Test
    void rejectField_setsVerificationStatusToRejected() {
        ApplicantProfile profile = newProfile();
        profile.setField(ApplicantProfile.PATH_PERSONAL_FULL_NAME, ExtractedField.selfDeclared("Jane Doe"));

        profile.rejectField(ApplicantProfile.PATH_PERSONAL_FULL_NAME);

        assertEquals(VerificationStatus.REJECTED, profile.personalInfo().fullName().verificationStatus());
    }

    @Test
    void setPurposeOfVisit_reflectedInTravelInfoView() {
        ApplicantProfile profile = newProfile();

        profile.setPurposeOfVisit("Tourism");

        assertEquals("Tourism", profile.travelInfo().purposeOfVisit());
    }

    @Test
    void getFields_returnsUnmodifiableMap() {
        ApplicantProfile profile = newProfile();
        profile.setField(ApplicantProfile.PATH_PERSONAL_FULL_NAME, ExtractedField.selfDeclared("Jane Doe"));

        Map<String, ExtractedField<?>> fields = profile.getFields();

        assertTrue(fields.containsKey(ApplicantProfile.PATH_PERSONAL_FULL_NAME));
        assertThrows(UnsupportedOperationException.class,
                () -> fields.put(ApplicantProfile.PATH_PERSONAL_GENDER, ExtractedField.selfDeclared("F")));
    }
}
