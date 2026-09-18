package com.neaz.travel.visa.application.command;

import java.util.UUID;

/**
 * Input to the "create an application" use case, built by
 * {@code ApplicationController} from a {@code POST
 * /api/v1/applicants/{applicantId}/applications} request.
 *
 * @param applicantId    the applicant this application belongs to
 * @param passportIso    ISO country code of the applicant's passport
 * @param destinationIso ISO country code of the visa-issuing destination
 * @param visaTypeCode   visa type code, e.g. {@code "TOURIST"}
 */
public record CreateApplicationCommand(UUID applicantId, String passportIso, String destinationIso, String visaTypeCode) {

    public CreateApplicationCommand {
        if (applicantId == null) {
            throw new IllegalArgumentException("applicantId must not be null");
        }
        if (passportIso == null || passportIso.isBlank()) {
            throw new IllegalArgumentException("passportIso must not be blank");
        }
        if (destinationIso == null || destinationIso.isBlank()) {
            throw new IllegalArgumentException("destinationIso must not be blank");
        }
        if (visaTypeCode == null || visaTypeCode.isBlank()) {
            throw new IllegalArgumentException("visaTypeCode must not be blank");
        }
    }
}
