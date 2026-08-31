package com.neaz.travel.visa.domain.model;

/**
 * A postal address, used as the value type inside {@link AddressInfo}'s
 * {@code permanentAddress}/{@code currentAddress} fields. Verified and
 * corrected as a single unit rather than line by line.
 *
 * @param line1      street address, first line
 * @param line2      street address, second line; {@code null} if not applicable
 * @param city       city or locality
 * @param postalCode postal/ZIP code; {@code null} where the country's addresses don't use one
 * @param countryIso ISO country code the address is in
 */
public record Address(String line1, String line2, String city, String postalCode, String countryIso) {

    public Address {
        if (line1 == null || line1.isBlank()) {
            throw new IllegalArgumentException("line1 must not be blank");
        }
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("city must not be blank");
        }
        if (countryIso == null || countryIso.isBlank()) {
            throw new IllegalArgumentException("countryIso must not be blank");
        }
    }
}
