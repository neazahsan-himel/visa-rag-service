package com.neaz.travel.visa.domain.service;

import com.neaz.travel.visa.domain.model.FeeComponent;
import com.neaz.travel.visa.domain.model.VisaFee;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@link FeeFreshnessPolicy} boundary: fresh through day
 * {@value FeeFreshnessPolicy#MAX_AGE_DAYS} after verification, stale from the
 * next day, with "today" taken from the clock's own time zone.
 */
class FeeFreshnessPolicyTest {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final LocalDate VERIFIED_AT = LocalDate.of(2026, 11, 1);

    @Test
    void isStale_onVerificationDay_returnsFalse() {
        assertFalse(policyAt(VERIFIED_AT).isStale(feeVerifiedAt(VERIFIED_AT)));
    }

    @Test
    void isStale_onLastFreshDay_returnsFalse() {
        LocalDate day90 = VERIFIED_AT.plusDays(FeeFreshnessPolicy.MAX_AGE_DAYS);

        assertFalse(policyAt(day90).isStale(feeVerifiedAt(VERIFIED_AT)));
    }

    @Test
    void isStale_onFirstStaleDay_returnsTrue() {
        LocalDate day91 = VERIFIED_AT.plusDays(FeeFreshnessPolicy.MAX_AGE_DAYS + 1L);

        assertTrue(policyAt(day91).isStale(feeVerifiedAt(VERIFIED_AT)));
    }

    @Test
    void isStale_withVerificationDateInFuture_returnsFalse() {
        assertFalse(policyAt(VERIFIED_AT).isStale(feeVerifiedAt(VERIFIED_AT.plusDays(1))));
    }

    @Test
    void isStale_usesClockTimeZoneForToday() {
        // 2027-01-30T18:30Z is still day 90 in UTC but already day 91 (00:30) in Dhaka (UTC+6).
        Instant instant = Instant.parse("2027-01-30T18:30:00Z");
        VisaFee fee = feeVerifiedAt(VERIFIED_AT);

        assertFalse(new FeeFreshnessPolicy(Clock.fixed(instant, ZoneOffset.UTC)).isStale(fee));
        assertTrue(new FeeFreshnessPolicy(Clock.fixed(instant, DHAKA)).isStale(fee));
    }

    @Test
    void staleFrom_returnsDayAfterMaxAge() {
        assertEquals(LocalDate.of(2027, 1, 31), policyAt(VERIFIED_AT).staleFrom(feeVerifiedAt(VERIFIED_AT)));
    }

    @Test
    void isStale_withNullFee_throwsNullPointerException() {
        FeeFreshnessPolicy policy = policyAt(VERIFIED_AT);

        assertThrows(NullPointerException.class, () -> policy.isStale(null));
    }

    @Test
    void constructor_withNullClock_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new FeeFreshnessPolicy(null));
    }

    private static FeeFreshnessPolicy policyAt(LocalDate today) {
        return new FeeFreshnessPolicy(Clock.fixed(today.atStartOfDay(DHAKA).toInstant(), DHAKA));
    }

    private static VisaFee feeVerifiedAt(LocalDate verifiedAt) {
        FeeComponent processing = new FeeComponent(FeeComponent.Type.PROCESSING, "Indian Visa Application Centre",
                new BigDecimal("800"), Currency.getInstance("BDT"));
        return new VisaFee(UUID.randomUUID(), "BD", "IN", "GENERAL", List.of(processing),
                "https://www.hcidhaka.gov.in/", "test quote", verifiedAt);
    }
}
