package com.neaz.travel.visa.application.result;

import com.neaz.travel.visa.domain.model.Application;
import com.neaz.travel.visa.domain.model.ApplicationStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Outcome of an application use case, returned by {@code ApplicationService}
 * to {@code ApplicationController}. Mirrors {@link Application} without
 * exposing the mutable entity itself.
 */
public record ApplicationResult(
        UUID id,
        UUID applicantId,
        String passportIso,
        String destinationIso,
        String visaTypeCode,
        ApplicationStatus status,
        Instant createdAt) {

    public ApplicationResult {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(applicantId, "applicantId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /** Maps a domain {@link Application} to this read-only result shape. */
    public static ApplicationResult from(Application application) {
        return new ApplicationResult(application.getId(), application.getApplicantId(), application.getPassportIso(),
                application.getDestinationIso(), application.getVisaTypeCode(), application.getStatus(),
                application.getCreatedAt());
    }
}
