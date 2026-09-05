package com.neaz.travel.visa.presentation.dto;

/**
 * Response for the diagnostic vision-preview endpoint. Mirrors {@code
 * VisualPassportData} field-for-field so a caller can see exactly what the
 * configured vision model read off an uploaded image, without running
 * OCR/MRZ/quality-gate/persistence — see {@code ApplicantDocumentController}.
 */
public record VisionPreviewResponse(
        FieldReadingDto passportNumber,
        FieldReadingDto dateOfBirth,
        FieldReadingDto expiryDate,
        FieldReadingDto issueDate,
        FieldReadingDto placeOfBirth,
        FieldReadingDto passportType) {

    /**
     * @param value           the raw value read, or {@code null} if not found
     * @param present         whether a non-blank value was found at all
     * @param clearlyReadable the model's own self-reported readability signal for this field
     */
    public record FieldReadingDto(String value, boolean present, boolean clearlyReadable) {
    }
}
