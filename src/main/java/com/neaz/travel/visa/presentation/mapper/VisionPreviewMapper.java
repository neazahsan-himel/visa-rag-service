package com.neaz.travel.visa.presentation.mapper;

import com.neaz.travel.visa.domain.model.VisualFieldReading;
import com.neaz.travel.visa.domain.model.VisualPassportData;
import com.neaz.travel.visa.presentation.dto.VisionPreviewResponse;
import org.springframework.stereotype.Component;

/** Translates {@link VisualPassportData} into the diagnostic vision-preview response DTO. */
@Component
public class VisionPreviewMapper {

    public VisionPreviewResponse toResponse(VisualPassportData data) {
        return new VisionPreviewResponse(
                toDto(data.passportNumber()),
                toDto(data.dateOfBirth()),
                toDto(data.expiryDate()),
                toDto(data.issueDate()),
                toDto(data.placeOfBirth()),
                toDto(data.passportType()));
    }

    private VisionPreviewResponse.FieldReadingDto toDto(VisualFieldReading reading) {
        return new VisionPreviewResponse.FieldReadingDto(
                reading.rawValue(), reading.isPresent(), reading.clearlyReadable());
    }
}
