package com.neaz.travel.visa.common.exception;

import java.util.UUID;

/**
 * Thrown when an {@code ApplicantProfile} lookup by applicant id finds no matching record.
 */
public class ApplicantProfileNotFoundException extends VisaRagException {

    private final UUID applicantId;

    public ApplicantProfileNotFoundException(UUID applicantId) {
        super("Applicant profile not found for applicantId: " + applicantId);
        this.applicantId = applicantId;
    }

    public UUID getApplicantId() {
        return applicantId;
    }
}
