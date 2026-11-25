package com.neaz.travel.visa.presentation.controller;

import com.neaz.travel.visa.application.query.VisaFeeQueryService;
import com.neaz.travel.visa.application.result.VisaFeeResult;
import com.neaz.travel.visa.domain.model.FeeComponent;
import com.neaz.travel.visa.domain.model.VisaFee;
import com.neaz.travel.visa.presentation.mapper.VisaFeeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer test for {@link VisaFeeController}: the JSON shape of a found fee,
 * the 404 insufficient-information response, and 400s from request
 * validation handled by {@link GlobalExceptionHandler}. The query service is
 * mocked; the real {@link VisaFeeMapper} is used.
 */
@WebMvcTest(VisaFeeController.class)
@Import(VisaFeeMapper.class)
class VisaFeeControllerTest {

    private static final Currency BDT = Currency.getInstance("BDT");
    private static final LocalDate VERIFIED_AT = LocalDate.of(2026, 11, 1);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VisaFeeQueryService visaFeeQueryService;

    @Test
    void getFee_whenFeeExists_returnsFeeJson() throws Exception {
        when(visaFeeQueryService.findFee("BD", "IN", "TOURIST")).thenReturn(Optional.of(generalFeeResult()));

        mockMvc.perform(get("/api/v1/visa/fees")
                        .param("passport", "BD").param("destination", "IN").param("visaType", "TOURIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestedVisaTypeCode").value("TOURIST"))
                .andExpect(jsonPath("$.data.appliedVisaTypeCode").value("GENERAL"))
                .andExpect(jsonPath("$.data.components.length()").value(2))
                .andExpect(jsonPath("$.data.components[0].type").value("BASIC"))
                .andExpect(jsonPath("$.data.components[0].amount").value(0.00))
                .andExpect(jsonPath("$.data.components[1].type").value("PROCESSING"))
                .andExpect(jsonPath("$.data.components[1].chargedBy").value("Indian Visa Application Centre"))
                .andExpect(jsonPath("$.data.components[1].amount").value(800.00))
                .andExpect(jsonPath("$.data.components[1].currency").value("BDT"))
                .andExpect(jsonPath("$.data.totalsByCurrency.BDT").value(800.00))
                .andExpect(jsonPath("$.data.sourceUrl").value("https://www.hcidhaka.gov.in/"))
                .andExpect(jsonPath("$.data.verifiedAt").value("2026-11-01"))
                .andExpect(jsonPath("$.data.stale").value(false))
                .andExpect(jsonPath("$.data.staleFrom").value("2027-01-31"));
    }

    @Test
    void getFee_whenNoFeeRecorded_returns404WithInsufficientInformationMessage() throws Exception {
        when(visaFeeQueryService.findFee(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/visa/fees")
                        .param("passport", "BD").param("destination", "GB").param("visaType", "TOURIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(VisaFeeController.NO_OFFICIAL_FEE_MESSAGE));
    }

    @Test
    void getFee_withMissingParameter_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/visa/fees").param("passport", "BD").param("destination", "IN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(visaFeeQueryService);
    }

    @Test
    void getFee_withBlankParameter_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/visa/fees")
                        .param("passport", "").param("destination", "IN").param("visaType", "TOURIST"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(visaFeeQueryService);
    }

    @Test
    void getFee_withLowercaseCountryCode_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/visa/fees")
                        .param("passport", "bd").param("destination", "IN").param("visaType", "TOURIST"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(visaFeeQueryService);
    }

    private static VisaFeeResult generalFeeResult() {
        VisaFee fee = new VisaFee(UUID.randomUUID(), "BD", "IN", "GENERAL",
                List.of(new FeeComponent(FeeComponent.Type.BASIC, "Government of India", BigDecimal.ZERO, BDT),
                        new FeeComponent(FeeComponent.Type.PROCESSING, "Indian Visa Application Centre",
                                new BigDecimal("800"), BDT)),
                "https://www.hcidhaka.gov.in/", "test quote", VERIFIED_AT);
        return VisaFeeResult.from(fee, "TOURIST", false, LocalDate.of(2027, 1, 31));
    }
}
