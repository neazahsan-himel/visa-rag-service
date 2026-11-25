package com.neaz.travel.visa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Provides the application-wide {@link Clock}. Date-based business rules
 * (e.g. {@code FeeFreshnessPolicy}'s 90-day window) take "today" from this
 * clock's zone, so it must be the zone of the market being served rather than
 * the server's default. An invalid {@code app.time-zone} fails startup instead
 * of silently shifting date boundaries.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.time-zone:Asia/Dhaka}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
