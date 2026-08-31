package com.neaz.travel.visa.application.applicant;

import com.neaz.travel.visa.domain.model.Address;
import com.neaz.travel.visa.domain.model.ApplicantProfile;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises every field-type branch {@link ApplicantFieldValueParser}
 * dispatches on, and the error cases each branch guards against.
 */
class ApplicantFieldValueParserTest {

    @Test
    void parseTypedValue_forStringPath_withStringValue_returnsString() {
        Object result = ApplicantFieldValueParser.parseTypedValue(
                ApplicantProfile.PATH_PERSONAL_FULL_NAME, "Jane Doe");

        assertEquals("Jane Doe", result);
    }

    @Test
    void parseTypedValue_forStringPath_withBlankValue_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                ApplicantFieldValueParser.parseTypedValue(ApplicantProfile.PATH_PERSONAL_FULL_NAME, "   "));
    }

    @Test
    void parseTypedValue_forStringPath_withNonStringValue_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                ApplicantFieldValueParser.parseTypedValue(ApplicantProfile.PATH_PERSONAL_FULL_NAME, 42));
    }

    @Test
    void parseTypedValue_forDatePath_withValidIsoDate_returnsLocalDate() {
        Object result = ApplicantFieldValueParser.parseTypedValue(
                ApplicantProfile.PATH_PERSONAL_DATE_OF_BIRTH, "1990-01-15");

        assertEquals(LocalDate.of(1990, 1, 15), result);
    }

    @Test
    void parseTypedValue_forDatePath_withInvalidFormat_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                ApplicantFieldValueParser.parseTypedValue(ApplicantProfile.PATH_PERSONAL_DATE_OF_BIRTH, "15/01/1990"));
    }

    @Test
    void parseTypedValue_forIntegerPath_withNumberValue_returnsInteger() {
        Object result = ApplicantFieldValueParser.parseTypedValue(
                ApplicantProfile.PATH_INTENDED_DURATION_DAYS, 14);

        assertEquals(14, result);
    }

    @Test
    void parseTypedValue_forIntegerPath_withNumericStringValue_returnsInteger() {
        Object result = ApplicantFieldValueParser.parseTypedValue(
                ApplicantProfile.PATH_INTENDED_DURATION_DAYS, "14");

        assertEquals(14, result);
    }

    @Test
    void parseTypedValue_forIntegerPath_withNonNumericValue_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                ApplicantFieldValueParser.parseTypedValue(ApplicantProfile.PATH_INTENDED_DURATION_DAYS, "two weeks"));
    }

    @Test
    void parseTypedValue_forAddressPath_withValidMap_returnsAddress() {
        Map<String, Object> addressMap = Map.of(
                "line1", "House 1", "city", "Dhaka", "postalCode", "1200", "countryIso", "BD");

        Object result = ApplicantFieldValueParser.parseTypedValue(
                ApplicantProfile.PATH_PERMANENT_ADDRESS, addressMap);

        assertEquals(new Address("House 1", null, "Dhaka", "1200", "BD"), result);
    }

    @Test
    void parseTypedValue_forAddressPath_withNonMapValue_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                ApplicantFieldValueParser.parseTypedValue(ApplicantProfile.PATH_PERMANENT_ADDRESS, "House 1, Dhaka"));
    }

    @Test
    void parseTypedValue_forAddressPath_withMissingRequiredSubField_throwsIllegalArgumentException() {
        Map<String, Object> incompleteAddress = Map.of("city", "Dhaka", "countryIso", "BD");

        assertThrows(IllegalArgumentException.class, () ->
                ApplicantFieldValueParser.parseTypedValue(ApplicantProfile.PATH_PERMANENT_ADDRESS, incompleteAddress));
    }

    @Test
    void parseTypedValue_forPurposeOfVisitPath_returnsString() {
        Object result = ApplicantFieldValueParser.parseTypedValue(
                ApplicantProfile.PATH_PURPOSE_OF_VISIT, "Tourism");

        assertEquals("Tourism", result);
    }

    @Test
    void parseTypedValue_forUnknownPath_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                ApplicantFieldValueParser.parseTypedValue("not.a.real.path", "value"));
    }
}
