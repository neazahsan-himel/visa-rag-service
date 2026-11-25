package com.neaz.travel.visa.domain.service;

import com.neaz.travel.visa.domain.model.VisaFee;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Decides whether a {@link VisaFee}'s last verification is recent enough to
 * present without a staleness warning.
 *
 * <p>A fee stays fresh for {@value #MAX_AGE_DAYS} days after its
 * {@code verifiedAt} date (inclusive) and is stale from the following day.
 * "Today" is taken from the injected {@link Clock} in that clock's own time
 * zone, so the boundary is deterministic in tests and follows the deployment's
 * configured zone in production.
 *
 * <p>A {@code verifiedAt} in the future is treated as fresh: it indicates a
 * data-entry error, which is not this policy's concern.
 */
public class FeeFreshnessPolicy {

    /** Number of days after verification during which a fee is still considered fresh. */
    public static final int MAX_AGE_DAYS = 90;

    private final Clock clock;

    public FeeFreshnessPolicy(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * @param fee the fee to assess
     * @return {@code true} if more than {@value #MAX_AGE_DAYS} days have passed since {@code fee.verifiedAt()}
     */
    public boolean isStale(VisaFee fee) {
        return !LocalDate.now(clock).isBefore(staleFrom(fee));
    }

    /**
     * @param fee the fee to assess
     * @return the first date on which {@code fee} is considered stale
     */
    public LocalDate staleFrom(VisaFee fee) {
        Objects.requireNonNull(fee, "fee must not be null");
        return fee.verifiedAt().plusDays(MAX_AGE_DAYS + 1L);
    }
}
