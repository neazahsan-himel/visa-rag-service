package com.neaz.travel.visa.application.applicant;

import com.neaz.travel.visa.domain.model.Address;
import com.neaz.travel.visa.domain.model.ApplicantProfile;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Converts a submitted value into the Java type an {@code ApplicantProfile}
 * field path declares. Shared by {@link ApplicantProfileService} (setting a
 * field) and {@link ApplicantProfileVerificationService} (correcting one) so
 * the field-path-to-type mapping exists in exactly one place — same
 * rationale as {@code common.util.Sha256Hasher}. Public since
 * {@code application.document.ExtractedFieldConfirmationService} is now a
 * second caller outside this package, for the same reason: one source of
 * truth for the field-path-to-type mapping.
 */
public final class ApplicantFieldValueParser {

    private ApplicantFieldValueParser() {
    }

    public static Object parseTypedValue(String fieldPath, Object value) {
        return switch (fieldPath) {
            case ApplicantProfile.PATH_PERSONAL_DATE_OF_BIRTH,
                 ApplicantProfile.PATH_PASSPORT_ISSUE_DATE,
                 ApplicantProfile.PATH_PASSPORT_EXPIRY_DATE,
                 ApplicantProfile.PATH_INTENDED_ARRIVAL_DATE -> requireLocalDate(value, fieldPath);
            case ApplicantProfile.PATH_INTENDED_DURATION_DAYS -> requireInteger(value, fieldPath);
            case ApplicantProfile.PATH_PERMANENT_ADDRESS, ApplicantProfile.PATH_CURRENT_ADDRESS ->
                    requireAddress(value, fieldPath);
            case ApplicantProfile.PATH_PERSONAL_FULL_NAME, ApplicantProfile.PATH_PERSONAL_GENDER,
                 ApplicantProfile.PATH_PERSONAL_PLACE_OF_BIRTH, ApplicantProfile.PATH_PASSPORT_NUMBER,
                 ApplicantProfile.PATH_PASSPORT_ISSUING_COUNTRY, ApplicantProfile.PATH_PASSPORT_TYPE,
                 ApplicantProfile.PATH_NATIONALITY, ApplicantProfile.PATH_PREVIOUS_NATIONALITY,
                 ApplicantProfile.PATH_FATHER_NAME, ApplicantProfile.PATH_MOTHER_NAME,
                 ApplicantProfile.PATH_SPOUSE_NAME, ApplicantProfile.PATH_MARITAL_STATUS,
                 ApplicantProfile.PATH_OCCUPATION, ApplicantProfile.PATH_EMPLOYER_NAME,
                 ApplicantProfile.PATH_EMPLOYER_ADDRESS, ApplicantProfile.PATH_MONTHLY_INCOME,
                 ApplicantProfile.PATH_PORT_OF_ENTRY, ApplicantProfile.PATH_PURPOSE_OF_VISIT -> requireString(value, fieldPath);
            default -> throw new IllegalArgumentException("Unknown applicant profile field path: " + fieldPath);
        };
    }

    private static String requireString(Object value, String fieldPath) {
        if (!(value instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("Field '" + fieldPath + "' requires a non-blank text value");
        }
        return s;
    }

    private static LocalDate requireLocalDate(Object value, String fieldPath) {
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException(
                    "Field '" + fieldPath + "' requires a date value formatted as ISO-8601 (yyyy-MM-dd)");
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Field '" + fieldPath + "' requires a date value formatted as ISO-8601 (yyyy-MM-dd)", e);
        }
    }

    private static Integer requireInteger(Object value, String fieldPath) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Field '" + fieldPath + "' requires a whole number value", e);
            }
        }
        throw new IllegalArgumentException("Field '" + fieldPath + "' requires a whole number value");
    }

    private static Address requireAddress(Object value, String fieldPath) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Field '" + fieldPath + "' requires an address object");
        }
        try {
            return new Address(
                    stringAt(map, "line1"),
                    stringAt(map, "line2"),
                    stringAt(map, "city"),
                    stringAt(map, "postalCode"),
                    stringAt(map, "countryIso"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Field '" + fieldPath + "' has an invalid address: " + e.getMessage(), e);
        }
    }

    private static String stringAt(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }
}
