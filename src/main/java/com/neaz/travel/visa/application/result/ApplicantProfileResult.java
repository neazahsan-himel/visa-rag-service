package com.neaz.travel.visa.application.result;

import com.neaz.travel.visa.domain.model.AddressInfo;
import com.neaz.travel.visa.domain.model.ApplicantProfile;
import com.neaz.travel.visa.domain.model.EmploymentInfo;
import com.neaz.travel.visa.domain.model.FamilyInfo;
import com.neaz.travel.visa.domain.model.NationalityInfo;
import com.neaz.travel.visa.domain.model.PassportInfo;
import com.neaz.travel.visa.domain.model.PersonalInfo;
import com.neaz.travel.visa.domain.model.TravelInfo;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Outcome of an applicant-profile use case, returned by
 * {@code application/applicant} services to {@code ApplicantProfileController}.
 * Mirrors {@code ApplicantProfile}'s typed views without exposing the
 * mutable entity itself.
 */
public record ApplicantProfileResult(
        UUID id,
        UUID applicantId,
        PersonalInfo personalInfo,
        PassportInfo passportInfo,
        NationalityInfo nationalityInfo,
        AddressInfo addressInfo,
        FamilyInfo familyInfo,
        EmploymentInfo employmentInfo,
        TravelInfo travelInfo,
        Instant lastModifiedAt) {

    public ApplicantProfileResult {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(applicantId, "applicantId must not be null");
        Objects.requireNonNull(lastModifiedAt, "lastModifiedAt must not be null");
    }

    /** Maps a domain {@link ApplicantProfile} to this read-only result shape. */
    public static ApplicantProfileResult from(ApplicantProfile profile) {
        return new ApplicantProfileResult(profile.getId(), profile.getApplicantId(), profile.personalInfo(),
                profile.passportInfo(), profile.nationalityInfo(), profile.addressInfo(), profile.familyInfo(),
                profile.employmentInfo(), profile.travelInfo(), profile.getLastModifiedAt());
    }
}
