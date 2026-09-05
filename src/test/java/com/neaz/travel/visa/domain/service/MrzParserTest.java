package com.neaz.travel.visa.domain.service;

import com.neaz.travel.visa.domain.model.MrzData;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Name-extraction coverage for {@link MrzParser}'s TD3 line 1 (document
 * code / issuing state / surname / given names). Line 2 (the
 * checksum-protected fields) is exercised separately by {@code
 * DocumentIntelligenceServiceTest}; here it is held fixed to one
 * structurally-valid 44-character line so only line 1 varies.
 */
class MrzParserTest {

    /**
     * A structurally-valid (correct length/charset) line 2. Its checksum
     * correctness is irrelevant to name extraction — {@code MrzParser}
     * reports check-digit validity as a separate flag on {@link MrzData}
     * and never lets it gate whether a line is parsed at all.
     */
    private static final String LINE_2 = "A053135959BGD9308189M32101563749469395<<<<34";

    private final MrzParser parser = new MrzParser();

    @Test
    void parsesExactRealPassportMrz() {
        // The actual TD3 line 1 from the passport in this bug report, read cleanly.
        MrzData mrz = parse(line1("CHOWDHURY", "MD", "NEAZ", "AHSAN"));

        assertEquals("CHOWDHURY", mrz.surname());
        assertEquals("MD NEAZ AHSAN", mrz.givenNames());
    }

    @Test
    void parsesMultipleGivenNames() {
        MrzData mrz = parse(line1("SMITH", "JOHN", "JACOB", "ROBERT", "JAMES"));

        assertEquals("SMITH", mrz.surname());
        assertEquals("JOHN JACOB ROBERT JAMES", mrz.givenNames());
    }

    @Test
    void parsesSurnameOnly_noGivenNamesPresent() {
        MrzData mrz = parse(line1("MADONNA"));

        assertEquals("MADONNA", mrz.surname());
        assertEquals("", mrz.givenNames());
    }

    @Test
    void nameFillsFieldExactly_noTrailingFillerPresent() {
        // 39-character name field with zero padding: surname(8) + "<<"(2) + given(29) = 39.
        // Confirms nothing is invented/truncated when the MRZ legitimately has no filler to strip.
        String given = "G".repeat(29);
        String nameField = "SURNAMEX<<" + given;
        assertEquals(39, nameField.length(), "test fixture itself must be a valid 39-char name field");

        MrzData mrz = parse("P<BGD" + nameField);

        assertEquals("SURNAMEX", mrz.surname());
        assertEquals(given, mrz.givenNames());
    }

    @Test
    void extraFillerBetweenGivenNames_truncatesAtThePaddingBoundaryInsteadOfGuessing() {
        // "JOHN<<PAUL": two consecutive '<' where a single separator was presumably intended.
        // Per ICAO 9303 the trailing padding run, once started, is unbroken '<' to the end of
        // the field -- so once two fillers appear back to back, MrzParser correctly treats
        // everything after that point as the padding zone and excludes it, rather than
        // guessing "PAUL" belongs in the name. This is the deliberate, conservative behavior
        // rule 4 (never invent characters) requires, not a defect.
        MrzData mrz = parse(line1DoubleSeparator("SURNAME", "JOHN", "PAUL"));

        assertEquals("SURNAME", mrz.surname());
        assertEquals("JOHN", mrz.givenNames());
    }

    @Test
    void ocrNoise_trailingLetterRunInsidePaddingIsExcludedFromName() {
        // Actual raw Tesseract output captured from this bug's real passport image (4x-upscaled
        // MRZ-band OCR pass): "AHSAN" itself read cleanly, but 3 characters inside the already-
        // established trailing filler run were misread as "SSE" instead of "<<<". Preceded by
        // 10 consecutive correctly-read '<' characters, "SSE" is unambiguously inside the padding
        // zone and must not leak into the name.
        String line1 = "P<BGDCHOWDHURY<<MD<NEAZ<AHSAN<<<<<<<<<<SSE<<";
        assertEquals(44, line1.length());

        MrzData mrz = parse(line1);

        assertEquals("CHOWDHURY", mrz.surname());
        assertEquals("MD NEAZ AHSAN", mrz.givenNames());
    }

    @Test
    void ocrNoise_letterFusedDirectlyOntoNameTokenIsHonestlyNotRecoverable() {
        // Actual raw Tesseract output captured at native (un-upscaled) crop resolution for the
        // same real passport image: "AHSAN" gained a fused extra "S" with no separator at all
        // ("AHSANS"), while a separate 7-character run further into the padding was misread as
        // "KKKKKKK". The "KKKKKKK" run is preceded by 7 consecutive '<' and is correctly excluded
        // the same way as the case above. "AHSANS", however, has no separator marking where the
        // real name ends and the corruption begins -- nothing in the MRZ's own structure
        // distinguishes a fused OCR error from a legitimately longer name, so per rule 4
        // (never invent characters) this parser does not attempt to guess "AHSAN" out of it.
        // That residual class of single-character-fusion error is out of MrzParser's reach by
        // design; it is caught downstream by DocumentIntelligenceService's cross-check against
        // the vision model's independent reading of the printed name.
        String line1 = "P<BGDCHOWDHURY<<MD<NEAZ<AHSANS<<<<<<<KKKKKKK";
        assertEquals(44, line1.length());

        MrzData mrz = parse(line1);

        assertEquals("CHOWDHURY", mrz.surname());
        assertEquals("MD NEAZ AHSANS", mrz.givenNames());
    }

    @Test
    void malformedMrz_lineOneCharacterShort_isNotParsed() {
        String shortLine1 = line1("CHOWDHURY", "MD", "NEAZ", "AHSAN").substring(1); // 43 chars
        assertEquals(43, shortLine1.length());

        assertParsesToEmpty(shortLine1);
    }

    @Test
    void malformedMrz_lineOneCharacterTooLong_isNotParsed() {
        String longLine1 = line1("CHOWDHURY", "MD", "NEAZ", "AHSAN") + "<"; // 45 chars
        assertEquals(45, longLine1.length());

        assertParsesToEmpty(longLine1);
    }

    @Test
    void malformedMrz_invalidCharacterOutsideMrzCharset_isNotParsed() {
        // '#' is never valid in an MRZ line, and unlike a stray lowercase letter (normalized to
        // uppercase before charset matching) there is no normalization that could make it valid.
        String invalid = "P#BGDCHOWDHURY<<MD<NEAZ<AHSAN<<<<<<<<<<<<<<<";
        assertEquals(44, invalid.length());

        assertParsesToEmpty(invalid);
    }

    @Test
    void malformedMrz_lineOneMissingDocumentCode_isNotParsed() {
        // A 44-character, charset-valid line that simply isn't a passport (doesn't start with 'P')
        // must not be mistaken for line 1.
        String notADocumentLine = "X<BGDCHOWDHURY<<MD<NEAZ<AHSAN<<<<<<<<<<<<<<<";
        assertEquals(44, notADocumentLine.length());

        assertParsesToEmpty(notADocumentLine);
    }

    @Test
    void malformedMrz_noMrzLinesInText_isNotParsed() {
        assertParsesToEmpty("This is just a cover letter with no MRZ content at all.");
    }

    @Test
    void validatesStrictlyOn44CharacterLines() {
        // Sanity check spanning both directions around the boundary in one place.
        String exact44 = line1("CHOWDHURY", "MD", "NEAZ", "AHSAN");
        assertEquals(44, exact44.length());
        assertTrue(parser.parse(exact44 + "\n" + LINE_2).isPresent());

        assertParsesToEmpty(exact44.substring(0, 43));
        assertParsesToEmpty(exact44 + "X");
    }

    private MrzData parse(String line1) {
        Optional<MrzData> result = parser.parse(line1 + "\n" + LINE_2);
        assertTrue(result.isPresent(), "expected MRZ line pair to be located and parsed");
        return result.get();
    }

    private void assertParsesToEmpty(String line1) {
        assertFalse(parser.parse(line1 + "\n" + LINE_2).isPresent());
    }

    /** Builds a TD3 line 1 for issuing state BGD, padding the name field to 39 characters. */
    private static String line1(String surname, String... givenNames) {
        String nameField = surname + "<<" + String.join("<", givenNames);
        StringBuilder padded = new StringBuilder(nameField);
        while (padded.length() < 39) {
            padded.append('<');
        }
        return "P<BGD" + padded;
    }

    /** Like {@link #line1}, but with two given names separated by a doubled '&lt;'. */
    private static String line1DoubleSeparator(String surname, String given1, String given2) {
        String nameField = surname + "<<" + given1 + "<<" + given2;
        StringBuilder padded = new StringBuilder(nameField);
        while (padded.length() < 39) {
            padded.append('<');
        }
        return "P<BGD" + padded;
    }
}
