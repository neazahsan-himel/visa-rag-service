package com.neaz.travel.visa.domain.service;

import com.neaz.travel.visa.domain.model.MrzData;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Locates and parses a passport's TD3 machine-readable zone (ICAO Doc 9303)
 * out of raw full-page OCR text, and validates every check digit the
 * standard defines (per-field plus the composite digit).
 *
 * <p>Scope: TD3 (passport, two 44-character lines) only — TD1/TD2 national
 * ID card formats are not handled, matching {@code DocumentType} currently
 * supporting only {@code PASSPORT}.
 *
 * <p>A returned {@link MrzData} may still carry failed checksums — this
 * class reports what it read and whether each check digit matched; it does
 * not decide whether the read is trustworthy enough to use. That decision
 * belongs to the caller (see {@link MrzData#allChecksPassed()}).
 */
public class MrzParser {

    private static final Pattern MRZ_LINE_CHARSET = Pattern.compile("^[A-Z0-9<]{44}$");
    private static final char FILLER = '<';
    private static final int[] CHECK_DIGIT_WEIGHTS = {7, 3, 1};

    /**
     * Attempts to find and parse a TD3 MRZ block anywhere in {@code ocrText}.
     * Empty when no pair of adjacent 44-character MRZ-charset lines starting
     * with a passport document code ('P') can be found.
     */
    public Optional<MrzData> parse(String ocrText) {
        Objects.requireNonNull(ocrText, "ocrText must not be null");
        return locateMrzLines(ocrText).map(lines -> parseLines(lines[0], lines[1]));
    }

    private Optional<String[]> locateMrzLines(String ocrText) {
        List<String> candidates = Arrays.stream(ocrText.split("\\r?\\n"))
                .map(MrzParser::normalizeLine)
                .filter(line -> MRZ_LINE_CHARSET.matcher(line).matches())
                .collect(Collectors.toList());

        for (int i = candidates.size() - 1; i >= 1; i--) {
            String line2 = candidates.get(i);
            String line1 = candidates.get(i - 1);
            if (line1.charAt(0) == 'P') {
                return Optional.of(new String[] {line1, line2});
            }
        }
        return Optional.empty();
    }

    private static String normalizeLine(String rawLine) {
        return rawLine.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private MrzData parseLines(String line1, String line2) {
        String documentCode = trimFiller(line1.substring(0, 2));
        String issuingCountry = trimFiller(line1.substring(2, 5));
        String[] names = parseName(line1.substring(5, 44));

        String passportNumberField = line2.substring(0, 9);
        char passportNumberCheckChar = line2.charAt(9);
        boolean passportNumberCheckValid = checkDigitMatches(passportNumberField, passportNumberCheckChar);

        String nationality = trimFiller(line2.substring(10, 13));

        String dobField = line2.substring(13, 19);
        char dobCheckChar = line2.charAt(19);
        boolean dobCheckValid = checkDigitMatches(dobField, dobCheckChar);

        char sexChar = line2.charAt(20);

        String expiryField = line2.substring(21, 27);
        char expiryCheckChar = line2.charAt(27);
        boolean expiryCheckValid = checkDigitMatches(expiryField, expiryCheckChar);

        String personalNumberField = line2.substring(28, 42);
        char personalNumberCheckChar = line2.charAt(42);
        boolean personalNumberCheckValid = personalNumberCheckChar == FILLER
                || checkDigitMatches(personalNumberField, personalNumberCheckChar);

        char compositeCheckChar = line2.charAt(43);
        String compositeData = passportNumberField + passportNumberCheckChar
                + dobField + dobCheckChar
                + expiryField + expiryCheckChar
                + personalNumberField + personalNumberCheckChar;
        boolean compositeCheckValid = checkDigitMatches(compositeData, compositeCheckChar);

        return new MrzData(
                documentCode,
                issuingCountry,
                names[0],
                names[1],
                trimFiller(passportNumberField),
                passportNumberCheckValid,
                nationality,
                parseMrzDate(dobField, true),
                dobCheckValid,
                sexChar == FILLER ? null : String.valueOf(sexChar),
                parseMrzDate(expiryField, false),
                expiryCheckValid,
                trimFiller(personalNumberField),
                personalNumberCheckValid,
                compositeCheckValid);
    }

    private static String[] parseName(String nameField) {
        int separatorIndex = nameField.indexOf("<<");
        String surnamePart = separatorIndex >= 0 ? nameField.substring(0, separatorIndex) : nameField;
        String givenPart = separatorIndex >= 0 ? nameField.substring(separatorIndex + 2) : "";
        return new String[] {cleanNamePart(surnamePart), parseGivenNames(givenPart)};
    }

    private static String cleanNamePart(String part) {
        return part.replace(FILLER, ' ').trim().replaceAll(" +", " ");
    }

    /**
     * Splits the given-names portion of the name field on single '&lt;'
     * separators (rule: a lone filler between two components is a name
     * separator, per ICAO 9303) and stops at the first empty split result,
     * which can only occur where two or more '&lt;' appear back to back —
     * i.e. where the trailing filler run has structurally begun.
     *
     * <p>ICAO 9303 defines that run as unbroken '&lt;' padding all the way
     * to the end of the field; nothing legitimate can follow once it starts.
     * A real given name is therefore never preceded by two consecutive
     * fillers within this field. Any token the OCR read *after* that point
     * (e.g. a misread filler glyph that came out as a stray letter) is
     * necessarily a padding-zone artifact and is dropped — never appended to
     * the name and never replaced with an invented value, only excluded, the
     * same way a value would be omitted entirely if it were unreadable.
     *
     * <p>This does not and cannot recover a corrupted character fused
     * directly onto a real token with no separator at all (e.g. an extra
     * letter stuck onto the last given name) — nothing in the MRZ's own
     * structure distinguishes that from a legitimately longer name, so no
     * check here is attempted for it; that residual class of error is left
     * for the applicant to catch via the vision-extraction cross-check.
     */
    private static String parseGivenNames(String givenPart) {
        String[] tokens = givenPart.split(String.valueOf(FILLER), -1);
        List<String> names = new ArrayList<>();
        for (String token : tokens) {
            if (token.isEmpty()) {
                break;
            }
            names.add(token);
        }
        return String.join(" ", names);
    }

    private static String trimFiller(String field) {
        return field.replace(FILLER, ' ').trim();
    }

    private static LocalDate parseMrzDate(String yymmdd, boolean birthDate) {
        if (!yymmdd.chars().allMatch(Character::isDigit)) {
            return null;
        }
        int yy = Integer.parseInt(yymmdd.substring(0, 2));
        int mm = Integer.parseInt(yymmdd.substring(2, 4));
        int dd = Integer.parseInt(yymmdd.substring(4, 6));

        LocalDate candidate = tryDate(2000 + yy, mm, dd);
        if (birthDate && candidate != null && candidate.isAfter(LocalDate.now())) {
            candidate = tryDate(1900 + yy, mm, dd);
        }
        return candidate;
    }

    private static LocalDate tryDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static boolean checkDigitMatches(String data, char declaredChar) {
        if (!Character.isDigit(declaredChar)) {
            return false;
        }
        return computeCheckDigit(data) == (declaredChar - '0');
    }

    /** ICAO 9303 check-digit algorithm: weights 7,3,1 cycling per character, summed mod 10. */
    private static int computeCheckDigit(String data) {
        int sum = 0;
        for (int i = 0; i < data.length(); i++) {
            sum += charValue(data.charAt(i)) * CHECK_DIGIT_WEIGHTS[i % 3];
        }
        return sum % 10;
    }

    private static int charValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'Z') {
            return (c - 'A') + 10;
        }
        return 0;
    }
}
