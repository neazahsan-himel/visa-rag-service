package com.neaz.travel.visa.application.port;

/**
 * Outbound port for extracting raw text from a document image via OCR.
 * Implemented in {@code infrastructure/document/ocr} using a self-hosted
 * Tesseract engine; the application layer depends only on this contract.
 */
public interface OcrPort {

    /**
     * Runs OCR over an uploaded document image and returns the raw
     * recognized text, unstructured (line breaks preserved where possible).
     *
     * @param imageBytes the raw uploaded image bytes (JPEG/PNG)
     * @return the recognized text
     */
    String extractText(byte[] imageBytes);
}
