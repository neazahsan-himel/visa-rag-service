package com.neaz.travel.visa.domain.model;

/**
 * Read-only view of an {@link ApplicantProfile}'s address fields, assembled
 * from its internal field map. Either component is {@code null} if that
 * field hasn't been set on the profile yet.
 *
 * @param permanentAddress the applicant's permanent/home address
 * @param currentAddress   the applicant's current address, if different from permanent
 */
public record AddressInfo(
        ExtractedField<Address> permanentAddress,
        ExtractedField<Address> currentAddress) {
}
