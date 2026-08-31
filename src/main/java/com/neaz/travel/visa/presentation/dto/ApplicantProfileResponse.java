package com.neaz.travel.visa.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response body for the applicant-profile endpoints, wrapped in
 * {@code ApiResponse<ApplicantProfileResponse>}. Mirrors {@code
 * ApplicantProfileResult}'s shape without referencing any domain type
 * directly.
 */
public record ApplicantProfileResponse(
        UUID id,
        UUID applicantId,
        PersonalInfoDto personalInfo,
        PassportInfoDto passportInfo,
        NationalityInfoDto nationalityInfo,
        AddressInfoDto addressInfo,
        FamilyInfoDto familyInfo,
        EmploymentInfoDto employmentInfo,
        TravelInfoDto travelInfo,
        Instant lastModifiedAt) {

    /** Wire shape of {@code ExtractedField<T>}. */
    public record ExtractedFieldDto<T>(
            T extractedValue, T normalizedValue, double confidence, UUID sourceDocumentId, String verificationStatus) {
    }

    /** Wire shape of {@code Address}. */
    public record AddressDto(String line1, String line2, String city, String postalCode, String countryIso) {
    }

    public record PersonalInfoDto(
            ExtractedFieldDto<String> fullName,
            ExtractedFieldDto<LocalDate> dateOfBirth,
            ExtractedFieldDto<String> gender,
            ExtractedFieldDto<String> placeOfBirth) {
    }

    public record PassportInfoDto(
            ExtractedFieldDto<String> passportNumber,
            ExtractedFieldDto<String> issuingCountry,
            ExtractedFieldDto<LocalDate> issueDate,
            ExtractedFieldDto<LocalDate> expiryDate,
            ExtractedFieldDto<String> passportType) {
    }

    public record NationalityInfoDto(
            ExtractedFieldDto<String> nationality,
            ExtractedFieldDto<String> previousNationality) {
    }

    public record AddressInfoDto(
            ExtractedFieldDto<AddressDto> permanentAddress,
            ExtractedFieldDto<AddressDto> currentAddress) {
    }

    public record FamilyInfoDto(
            ExtractedFieldDto<String> fatherName,
            ExtractedFieldDto<String> motherName,
            ExtractedFieldDto<String> spouseName,
            ExtractedFieldDto<String> maritalStatus) {
    }

    public record EmploymentInfoDto(
            ExtractedFieldDto<String> occupation,
            ExtractedFieldDto<String> employerName,
            ExtractedFieldDto<String> employerAddress,
            ExtractedFieldDto<String> monthlyIncome) {
    }

    public record TravelInfoDto(
            String purposeOfVisit,
            ExtractedFieldDto<LocalDate> intendedArrivalDate,
            ExtractedFieldDto<Integer> intendedDurationDays,
            ExtractedFieldDto<String> portOfEntry) {
    }
}
