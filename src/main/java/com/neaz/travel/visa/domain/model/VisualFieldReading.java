package com.neaz.travel.visa.domain.model;

/**
 * One field as read off the visual passport page by the multimodal
 * extraction model, before format/plausibility validation. {@code
 * clearlyReadable} is the model's own self-reported readability signal for
 * this specific field (label clearly detected and value clearly legible),
 * used together with format validation to place the resulting candidate
 * into the HIGH/MEDIUM/LOW visual-confidence tier — see {@code
 * DocumentIntelligenceService}.
 *
 * <p>Unlike {@link MrzData}, there is no checksum here: a vision model has
 * no cryptographic self-check, only this qualitative readability signal.
 */
public record VisualFieldReading(String rawValue, boolean clearlyReadable) {

    /** No value was visible/found on the page for this field. */
    public static final VisualFieldReading NOT_FOUND = new VisualFieldReading(null, false);

    public boolean isPresent() {
        return rawValue != null && !rawValue.isBlank();
    }
}
