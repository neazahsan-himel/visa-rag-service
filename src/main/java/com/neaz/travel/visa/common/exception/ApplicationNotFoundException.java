package com.neaz.travel.visa.common.exception;

import java.util.UUID;

/**
 * Thrown when an {@code Application} lookup by id finds no matching record,
 * or the caller's applicantId does not own the matched application.
 */
public class ApplicationNotFoundException extends VisaRagException {

    private final UUID applicationId;

    public ApplicationNotFoundException(UUID applicationId) {
        super("Application not found: " + applicationId);
        this.applicationId = applicationId;
    }

    public UUID getApplicationId() {
        return applicationId;
    }
}
