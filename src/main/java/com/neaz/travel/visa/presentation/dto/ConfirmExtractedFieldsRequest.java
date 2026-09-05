package com.neaz.travel.visa.presentation.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

/**
 * Request body for {@code POST /api/v1/applicants/{applicantId}/documents/{documentId}/confirm}.
 * {@code applicantId} and {@code documentId} come from the URL path, not this body.
 *
 * @param fields the candidates the applicant chose to keep (unchanged or edited), keyed by
 *               {@code ApplicantProfile}'s {@code PATH_*} constants; a candidate the applicant
 *               discards is simply left out, never included here
 */
public record ConfirmExtractedFieldsRequest(@NotEmpty Map<String, Object> fields) {
}
