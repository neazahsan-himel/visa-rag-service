package com.neaz.travel.visa.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request body for {@code POST /api/v1/ingestion/documents}.
 *
 * @param path     the document's location, relative to the knowledge-base root
 * @param title    a human-readable title for the document
 * @param metadata the document's country/visa-type/passport/source metadata
 */
public record IngestionRequest(
        @NotBlank String path,
        @NotBlank String title,
        @NotNull @Valid MetadataDto metadata) {

    /**
     * Wire shape of {@code DocumentMetadata}.
     */
    public record MetadataDto(
            @NotBlank String destinationIso,
            @NotBlank String passportIso,
            @NotBlank String visaTypeCode,
            String sourceUrl,
            @NotNull LocalDate lastUpdated,
            String version) {
    }
}