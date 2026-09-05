package com.neaz.travel.visa.domain.model;

import java.util.Objects;

/**
 * Fields read from the visual (non-MRZ) area of a passport bio-data page by
 * the multimodal extraction model. Carries two kinds of fields:
 *
 * <ul>
 *   <li>fields the MRZ never encodes ({@code issueDate}, {@code placeOfBirth},
 *       {@code passportType}) — their only possible source is this page read;
 *   <li>fields the MRZ also encodes ({@code passportNumber}, {@code dateOfBirth},
 *       {@code expiryDate}, and — split into {@code surname}/{@code givenNames}
 *       the same way {@code MrzData} splits them — the holder's name) — kept
 *       here purely so {@code DocumentIntelligenceService} can cross-check the
 *       printed value against the MRZ-decoded one.
 * </ul>
 *
 * <p>Every field is a {@link VisualFieldReading}; {@link VisualFieldReading#NOT_FOUND}
 * means the model could not locate that field on the page — never a guessed value.
 */
public record VisualPassportData(
        VisualFieldReading passportNumber,
        VisualFieldReading dateOfBirth,
        VisualFieldReading expiryDate,
        VisualFieldReading issueDate,
        VisualFieldReading placeOfBirth,
        VisualFieldReading passportType,
        VisualFieldReading surname,
        VisualFieldReading givenNames) {

    public VisualPassportData {
        Objects.requireNonNull(passportNumber, "passportNumber must not be null");
        Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");
        Objects.requireNonNull(expiryDate, "expiryDate must not be null");
        Objects.requireNonNull(issueDate, "issueDate must not be null");
        Objects.requireNonNull(placeOfBirth, "placeOfBirth must not be null");
        Objects.requireNonNull(passportType, "passportType must not be null");
        Objects.requireNonNull(surname, "surname must not be null");
        Objects.requireNonNull(givenNames, "givenNames must not be null");
    }

    /** No fields located — used when the vision model call fails or its output cannot be parsed. */
    public static VisualPassportData empty() {
        return new VisualPassportData(VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND,
                VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND,
                VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND, VisualFieldReading.NOT_FOUND);
    }
}
