package com.neaz.travel.visa.application.port;

import com.neaz.travel.visa.domain.model.VisualPassportData;

/**
 * Outbound port for reading fields off the visual (non-MRZ) area of a
 * passport bio-data page via a multimodal/vision-capable chat model.
 * Implemented in {@code infrastructure/document/vision} against the same
 * chat model configured for RAG answer generation; the application layer
 * depends only on this contract.
 */
public interface VisionExtractionPort {

    /**
     * Reads the fields a vision model can find on the passport bio-data
     * page. Never guesses: a field not confidently located comes back as
     * {@link com.neaz.travel.visa.domain.model.VisualFieldReading#NOT_FOUND}.
     *
     * @param imageBytes the raw uploaded image bytes (JPEG/PNG)
     * @return the fields read, one {@code VisualFieldReading} per field
     */
    VisualPassportData extract(byte[] imageBytes);
}
