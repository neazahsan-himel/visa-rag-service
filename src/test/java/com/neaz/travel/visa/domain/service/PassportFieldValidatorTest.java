package com.neaz.travel.visa.domain.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plausibility/format checks applied to visual-page readings before they
 * become field candidates. These are heuristics, not proof of correctness —
 * these tests only pin down which side of each check a given value falls on.
 */
class PassportFieldValidatorTest {

    @Test
    void isPlausibleDateOfBirth_pastDate_isPlausible() {
        assertTrue(PassportFieldValidator.isPlausibleDateOfBirth(LocalDate.now().minusYears(30)));
    }

    @Test
    void isPlausibleDateOfBirth_futureDate_isNotPlausible() {
        assertFalse(PassportFieldValidator.isPlausibleDateOfBirth(LocalDate.now().plusDays(1)));
    }

    @Test
    void isPlausibleDateOfBirth_tooFarInThePast_isNotPlausible() {
        assertFalse(PassportFieldValidator.isPlausibleDateOfBirth(LocalDate.now().minusYears(200)));
    }

    @Test
    void isPlausibleDateOfBirth_null_isNotPlausible() {
        assertFalse(PassportFieldValidator.isPlausibleDateOfBirth(null));
    }

    @Test
    void isPlausibleExpiryDate_afterIssueDate_isPlausible() {
        LocalDate issueDate = LocalDate.of(2020, 1, 1);
        LocalDate expiryDate = LocalDate.of(2030, 1, 1);
        assertTrue(PassportFieldValidator.isPlausibleExpiryDate(expiryDate, issueDate));
    }

    @Test
    void isPlausibleExpiryDate_beforeIssueDate_isNotPlausible() {
        LocalDate issueDate = LocalDate.of(2030, 1, 1);
        LocalDate expiryDate = LocalDate.of(2020, 1, 1);
        assertFalse(PassportFieldValidator.isPlausibleExpiryDate(expiryDate, issueDate));
    }

    @Test
    void isPlausibleExpiryDate_noIssueDateToCompare_isPlausibleIfPresent() {
        assertTrue(PassportFieldValidator.isPlausibleExpiryDate(LocalDate.of(2030, 1, 1), null));
    }

    @Test
    void isPlausibleExpiryDate_null_isNotPlausible() {
        assertFalse(PassportFieldValidator.isPlausibleExpiryDate(null, null));
    }

    @Test
    void isPlausibleIssueDate_pastAndBeforeExpiry_isPlausible() {
        LocalDate issueDate = LocalDate.now().minusYears(2);
        LocalDate expiryDate = LocalDate.now().plusYears(8);
        assertTrue(PassportFieldValidator.isPlausibleIssueDate(issueDate, expiryDate));
    }

    @Test
    void isPlausibleIssueDate_inTheFuture_isNotPlausible() {
        assertFalse(PassportFieldValidator.isPlausibleIssueDate(LocalDate.now().plusDays(1), null));
    }

    @Test
    void isValidPassportNumber_wellFormed_isValid() {
        assertTrue(PassportFieldValidator.isValidPassportNumber("A1234567"));
    }

    @Test
    void isValidPassportNumber_tooShort_isInvalid() {
        assertFalse(PassportFieldValidator.isValidPassportNumber("A123"));
    }

    @Test
    void isValidPassportNumber_containsInvalidCharacters_isInvalid() {
        assertFalse(PassportFieldValidator.isValidPassportNumber("A123-567"));
    }

    @Test
    void isValidPassportNumber_null_isInvalid() {
        assertFalse(PassportFieldValidator.isValidPassportNumber(null));
    }

    @Test
    void isValidPassportType_shortAlphabeticLabel_isValid() {
        assertTrue(PassportFieldValidator.isValidPassportType("P"));
        assertTrue(PassportFieldValidator.isValidPassportType("Ordinary"));
    }

    @Test
    void isValidPassportType_containsDigits_isInvalid() {
        assertFalse(PassportFieldValidator.isValidPassportType("P1"));
    }

    @Test
    void isValidPlaceOfBirth_nonBlankReasonableLength_isValid() {
        assertTrue(PassportFieldValidator.isValidPlaceOfBirth("Dhaka"));
    }

    @Test
    void isValidPlaceOfBirth_blank_isInvalid() {
        assertFalse(PassportFieldValidator.isValidPlaceOfBirth("   "));
    }

    @Test
    void isValidPlaceOfBirth_null_isInvalid() {
        assertFalse(PassportFieldValidator.isValidPlaceOfBirth(null));
    }
}
