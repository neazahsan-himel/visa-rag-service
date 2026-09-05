package com.neaz.travel.visa.domain.service;

import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Format/plausibility checks applied to a field read off the visual passport
 * page before it may become a field candidate (see {@code
 * DocumentIntelligenceService}). MRZ-derived fields are not validated here —
 * their trust signal is the MRZ check digit (see {@code MrzParser}), which
 * this class has no equivalent for.
 *
 * <p>These are plausibility checks, not proof of correctness: a value can
 * pass every check here and still be wrong. Passing/failing only feeds into
 * which visual-confidence tier a candidate is assigned.
 */
public final class PassportFieldValidator {

    /** A birth date this far in the past is no longer plausible for a living applicant. */
    private static final int MAX_PLAUSIBLE_AGE_YEARS = 130;

    private static final int PASSPORT_NUMBER_MIN_LENGTH = 6;
    private static final int PASSPORT_NUMBER_MAX_LENGTH = 9;
    private static final Pattern PASSPORT_NUMBER_PATTERN =
            Pattern.compile("^[A-Z0-9]{" + PASSPORT_NUMBER_MIN_LENGTH + "," + PASSPORT_NUMBER_MAX_LENGTH + "}$");

    private static final int PASSPORT_TYPE_MAX_LENGTH = 20;
    private static final Pattern PASSPORT_TYPE_PATTERN =
            Pattern.compile("^[A-Za-z][A-Za-z ]{0," + (PASSPORT_TYPE_MAX_LENGTH - 1) + "}$");

    private static final int PLACE_OF_BIRTH_MAX_LENGTH = 100;

    private static final int FULL_NAME_MAX_LENGTH = 100;
    private static final Pattern FULL_NAME_PATTERN = Pattern.compile("^[A-Za-z' -]+$");

    private PassportFieldValidator() {
    }

    /** Not in the future, and not further back than a plausible human lifespan. */
    public static boolean isPlausibleDateOfBirth(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        if (dateOfBirth.isAfter(today)) {
            return false;
        }
        return Period.between(dateOfBirth, today).getYears() <= MAX_PLAUSIBLE_AGE_YEARS;
    }

    /** After the issue date, when an issue date is available to compare against. */
    public static boolean isPlausibleExpiryDate(LocalDate expiryDate, LocalDate issueDate) {
        if (expiryDate == null) {
            return false;
        }
        return issueDate == null || expiryDate.isAfter(issueDate);
    }

    /** Not in the future, and before the expiry date when one is available to compare against. */
    public static boolean isPlausibleIssueDate(LocalDate issueDate, LocalDate expiryDate) {
        if (issueDate == null) {
            return false;
        }
        if (issueDate.isAfter(LocalDate.now())) {
            return false;
        }
        return expiryDate == null || expiryDate.isAfter(issueDate);
    }

    /** Matches the alphanumeric length range passport numbers conventionally use. */
    public static boolean isValidPassportNumber(String passportNumber) {
        return passportNumber != null
                && PASSPORT_NUMBER_PATTERN.matcher(passportNumber.trim().toUpperCase(Locale.ROOT)).matches();
    }

    /** A short alphabetic document-type label (e.g. "P", "Ordinary", "Diplomatic"). */
    public static boolean isValidPassportType(String passportType) {
        return passportType != null && PASSPORT_TYPE_PATTERN.matcher(passportType.trim()).matches();
    }

    /** Non-blank and within a sane length for a place name. */
    public static boolean isValidPlaceOfBirth(String placeOfBirth) {
        return placeOfBirth != null && !placeOfBirth.isBlank()
                && placeOfBirth.trim().length() <= PLACE_OF_BIRTH_MAX_LENGTH;
    }

    /** Letters, spaces, apostrophes and hyphens only, within a sane length for a person's full name. */
    public static boolean isValidFullName(String fullName) {
        if (fullName == null) {
            return false;
        }
        String trimmed = fullName.trim();
        return !trimmed.isEmpty() && trimmed.length() <= FULL_NAME_MAX_LENGTH
                && FULL_NAME_PATTERN.matcher(trimmed).matches();
    }
}
