package com.neaz.travel.visa.application.query;

import com.neaz.travel.visa.application.result.VisaFeeResult;
import com.neaz.travel.visa.domain.model.FeeComponent;
import com.neaz.travel.visa.domain.model.VisaFee;
import com.neaz.travel.visa.domain.repository.VisaFeeRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VisaFeeQueryService}: exact-type precedence, the
 * {@code GENERAL} fallback, the empty result when no fee is recorded, and the
 * freshness verdict taken from a fixed clock.
 */
class VisaFeeQueryServiceTest {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final LocalDate VERIFIED_AT = LocalDate.of(2026, 11, 1);
    private static final Currency BDT = Currency.getInstance("BDT");

    private final VisaFeeRepository visaFeeRepository = mock(VisaFeeRepository.class);

    @Test
    void findFee_whenExactTypeFeeExists_returnsItWithoutFallback() {
        VisaFee touristFee = fee("TOURIST", "1000");
        when(visaFeeRepository.findActive("BD", "IN", "TOURIST")).thenReturn(Optional.of(touristFee));

        VisaFeeResult result = serviceAt(VERIFIED_AT).findFee("BD", "IN", "TOURIST").orElseThrow();

        assertEquals("TOURIST", result.requestedVisaTypeCode());
        assertEquals("TOURIST", result.appliedVisaTypeCode());
        assertEquals(Map.of(BDT, new BigDecimal("1000.00")), result.totalsByCurrency());
        verify(visaFeeRepository, never()).findActive("BD", "IN", "GENERAL");
    }

    @Test
    void findFee_whenOnlyGeneralFeeExists_fallsBackAndReportsAppliedType() {
        when(visaFeeRepository.findActive("BD", "IN", "TOURIST")).thenReturn(Optional.empty());
        when(visaFeeRepository.findActive("BD", "IN", "GENERAL")).thenReturn(Optional.of(fee("GENERAL", "800")));

        VisaFeeResult result = serviceAt(VERIFIED_AT).findFee("BD", "IN", "TOURIST").orElseThrow();

        assertEquals("TOURIST", result.requestedVisaTypeCode());
        assertEquals("GENERAL", result.appliedVisaTypeCode());
        assertEquals(Map.of(BDT, new BigDecimal("800.00")), result.totalsByCurrency());
        assertEquals("https://www.hcidhaka.gov.in/", result.sourceUrl());
        assertEquals("test quote", result.sourceQuote());
        assertEquals(VERIFIED_AT, result.verifiedAt());
    }

    @Test
    void findFee_whenNoFeeRecorded_returnsEmpty() {
        when(visaFeeRepository.findActive(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        assertTrue(serviceAt(VERIFIED_AT).findFee("BD", "IN", "TOURIST").isEmpty());
    }

    @Test
    void findFee_whenGeneralRequested_doesNotLookUpGeneralTwice() {
        when(visaFeeRepository.findActive("BD", "IN", "GENERAL")).thenReturn(Optional.empty());

        assertTrue(serviceAt(VERIFIED_AT).findFee("BD", "IN", "GENERAL").isEmpty());
        verify(visaFeeRepository, times(1)).findActive("BD", "IN", "GENERAL");
    }

    @Test
    void findFee_withinFreshnessWindow_isNotStale() {
        when(visaFeeRepository.findActive("BD", "IN", "GENERAL")).thenReturn(Optional.of(fee("GENERAL", "800")));

        VisaFeeResult result = serviceAt(VERIFIED_AT.plusDays(90)).findFee("BD", "IN", "GENERAL").orElseThrow();

        assertFalse(result.stale());
        assertEquals(LocalDate.of(2027, 1, 31), result.staleFrom());
    }

    @Test
    void findFee_afterFreshnessWindow_isStale() {
        when(visaFeeRepository.findActive("BD", "IN", "GENERAL")).thenReturn(Optional.of(fee("GENERAL", "800")));

        VisaFeeResult result = serviceAt(VERIFIED_AT.plusDays(91)).findFee("BD", "IN", "GENERAL").orElseThrow();

        assertTrue(result.stale());
    }

    private VisaFeeQueryService serviceAt(LocalDate today) {
        Clock clock = Clock.fixed(today.atStartOfDay(DHAKA).toInstant(), DHAKA);
        return new VisaFeeQueryService(visaFeeRepository, clock);
    }

    private static VisaFee fee(String visaTypeCode, String processingAmount) {
        List<FeeComponent> components = List.of(
                new FeeComponent(FeeComponent.Type.BASIC, "Government of India", BigDecimal.ZERO, BDT),
                new FeeComponent(FeeComponent.Type.PROCESSING, "Indian Visa Application Centre",
                        new BigDecimal(processingAmount), BDT));
        return new VisaFee(UUID.randomUUID(), "BD", "IN", visaTypeCode, components,
                "https://www.hcidhaka.gov.in/", "test quote", VERIFIED_AT);
    }
}
