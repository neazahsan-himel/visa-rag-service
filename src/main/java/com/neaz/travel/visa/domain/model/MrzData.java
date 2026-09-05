package com.neaz.travel.visa.domain.model;

import java.time.LocalDate;

/**
 * Fields parsed from a passport's TD3 machine-readable zone (ICAO Doc 9303),
 * together with per-field checksum validity. A failed checksum does not
 * discard the value — {@code MrzParser} still returns its best-effort
 * reading — but callers must treat an invalid checksum as low-confidence and
 * never write it into an {@link ApplicantProfile} without applicant review.
 */
public record MrzData(
        String documentCode,
        String issuingCountry,
        String surname,
        String givenNames,
        String passportNumber,
        boolean passportNumberCheckValid,
        String nationality,
        LocalDate dateOfBirth,
        boolean dateOfBirthCheckValid,
        String sex,
        LocalDate expirationDate,
        boolean expirationDateCheckValid,
        String personalNumber,
        boolean personalNumberCheckValid,
        boolean compositeCheckValid) {

    /**
     * True only if every checksum present in the MRZ passed, including the
     * personal-number check when a personal number is present. A single
     * failed checksum is enough to distrust the whole read.
     */
    public boolean allChecksPassed() {
        boolean personalNumberOk = personalNumber == null || personalNumber.isBlank() || personalNumberCheckValid;
        return passportNumberCheckValid && dateOfBirthCheckValid && expirationDateCheckValid
                && personalNumberOk && compositeCheckValid;
    }
}
