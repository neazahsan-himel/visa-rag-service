package com.neaz.travel.visa.presentation.mapper;

import com.neaz.travel.visa.application.command.SetApplicantFieldCommand;
import com.neaz.travel.visa.application.command.VerifyApplicantFieldCommand;
import com.neaz.travel.visa.application.result.ApplicantProfileResult;
import com.neaz.travel.visa.domain.model.Address;
import com.neaz.travel.visa.domain.model.AddressInfo;
import com.neaz.travel.visa.domain.model.EmploymentInfo;
import com.neaz.travel.visa.domain.model.ExtractedField;
import com.neaz.travel.visa.domain.model.FamilyInfo;
import com.neaz.travel.visa.domain.model.NationalityInfo;
import com.neaz.travel.visa.domain.model.PassportInfo;
import com.neaz.travel.visa.domain.model.PersonalInfo;
import com.neaz.travel.visa.domain.model.TravelInfo;
import com.neaz.travel.visa.presentation.dto.ApplicantProfileResponse;
import com.neaz.travel.visa.presentation.dto.SetApplicantFieldRequest;
import com.neaz.travel.visa.presentation.dto.VerifyApplicantFieldRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Translates between applicant-profile DTOs and the application layer's
 * command and result types.
 */
@Component
public class ApplicantProfileMapper {

    public SetApplicantFieldCommand toCommand(UUID applicantId, SetApplicantFieldRequest request) {
        return new SetApplicantFieldCommand(applicantId, request.fieldPath(), request.value());
    }

    public VerifyApplicantFieldCommand toCommand(UUID applicantId, VerifyApplicantFieldRequest request) {
        VerifyApplicantFieldCommand.Action action = VerifyApplicantFieldCommand.Action.valueOf(request.action());
        return new VerifyApplicantFieldCommand(applicantId, request.fieldPath(), action, request.correctedValue());
    }

    public ApplicantProfileResponse toResponse(ApplicantProfileResult result) {
        return new ApplicantProfileResponse(
                result.id(),
                result.applicantId(),
                toDto(result.personalInfo()),
                toDto(result.passportInfo()),
                toDto(result.nationalityInfo()),
                toDto(result.addressInfo()),
                toDto(result.familyInfo()),
                toDto(result.employmentInfo()),
                toDto(result.travelInfo()),
                result.lastModifiedAt());
    }

    private ApplicantProfileResponse.PersonalInfoDto toDto(PersonalInfo info) {
        return new ApplicantProfileResponse.PersonalInfoDto(
                toFieldDto(info.fullName()), toFieldDto(info.dateOfBirth()),
                toFieldDto(info.gender()), toFieldDto(info.placeOfBirth()));
    }

    private ApplicantProfileResponse.PassportInfoDto toDto(PassportInfo info) {
        return new ApplicantProfileResponse.PassportInfoDto(
                toFieldDto(info.passportNumber()), toFieldDto(info.issuingCountry()),
                toFieldDto(info.issueDate()), toFieldDto(info.expiryDate()), toFieldDto(info.passportType()));
    }

    private ApplicantProfileResponse.NationalityInfoDto toDto(NationalityInfo info) {
        return new ApplicantProfileResponse.NationalityInfoDto(
                toFieldDto(info.nationality()), toFieldDto(info.previousNationality()));
    }

    private ApplicantProfileResponse.AddressInfoDto toDto(AddressInfo info) {
        return new ApplicantProfileResponse.AddressInfoDto(
                toAddressFieldDto(info.permanentAddress()), toAddressFieldDto(info.currentAddress()));
    }

    private ApplicantProfileResponse.FamilyInfoDto toDto(FamilyInfo info) {
        return new ApplicantProfileResponse.FamilyInfoDto(
                toFieldDto(info.fatherName()), toFieldDto(info.motherName()),
                toFieldDto(info.spouseName()), toFieldDto(info.maritalStatus()));
    }

    private ApplicantProfileResponse.EmploymentInfoDto toDto(EmploymentInfo info) {
        return new ApplicantProfileResponse.EmploymentInfoDto(
                toFieldDto(info.occupation()), toFieldDto(info.employerName()),
                toFieldDto(info.employerAddress()), toFieldDto(info.monthlyIncome()));
    }

    private ApplicantProfileResponse.TravelInfoDto toDto(TravelInfo info) {
        return new ApplicantProfileResponse.TravelInfoDto(
                info.purposeOfVisit(), toFieldDto(info.intendedArrivalDate()),
                toFieldDto(info.intendedDurationDays()), toFieldDto(info.portOfEntry()));
    }

    private <T> ApplicantProfileResponse.ExtractedFieldDto<T> toFieldDto(ExtractedField<T> field) {
        if (field == null) {
            return null;
        }
        return new ApplicantProfileResponse.ExtractedFieldDto<>(
                field.extractedValue(), field.normalizedValue(), field.confidence(),
                field.sourceDocumentId(), field.verificationStatus().name());
    }

    private ApplicantProfileResponse.ExtractedFieldDto<ApplicantProfileResponse.AddressDto> toAddressFieldDto(
            ExtractedField<Address> field) {
        if (field == null) {
            return null;
        }
        return new ApplicantProfileResponse.ExtractedFieldDto<>(
                toAddressDto(field.extractedValue()), toAddressDto(field.normalizedValue()), field.confidence(),
                field.sourceDocumentId(), field.verificationStatus().name());
    }

    private ApplicantProfileResponse.AddressDto toAddressDto(Address address) {
        if (address == null) {
            return null;
        }
        return new ApplicantProfileResponse.AddressDto(
                address.line1(), address.line2(), address.city(), address.postalCode(), address.countryIso());
    }
}
