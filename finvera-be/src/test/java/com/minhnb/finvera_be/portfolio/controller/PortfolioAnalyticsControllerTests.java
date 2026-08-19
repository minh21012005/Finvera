package com.minhnb.finvera_be.portfolio.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.controller.OwnerAccessController;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioAnalyticsV1;
import com.minhnb.finvera_be.portfolio.dto.BenchmarkComparisonResponse;
import com.minhnb.finvera_be.portfolio.dto.ConcentrationEntryResponse;
import com.minhnb.finvera_be.portfolio.dto.PortfolioAnalyticsResponse;
import com.minhnb.finvera_be.portfolio.dto.RiskExposureResponse;
import com.minhnb.finvera_be.portfolio.service.PortfolioAnalyticsService;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.PeriodTooLongException;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.PortfolioNotFoundException;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = {OwnerAccessController.class, PortfolioAnalyticsController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class, ProblemDetailsAdvice.class})
class PortfolioAnalyticsControllerTests {

    private static final String OWNER_NAME = "owner-" + UUID.randomUUID();
    private static final String LOGIN_PROOF = UUID.randomUUID().toString();
    private static final String LOGIN_PROOF_HASH = new BCryptPasswordEncoder(4).encode(LOGIN_PROOF);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @DynamicPropertySource
    static void ownerProperties(DynamicPropertyRegistry registry) {
        registry.add("finvera.security.owner.id", () -> "00000000-0000-0000-0000-000000000001");
        registry.add("finvera.security.owner.username", () -> OWNER_NAME);
        registry.add("finvera.security.owner.password-hash", () -> LOGIN_PROOF_HASH);
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PortfolioAnalyticsService analyticsService;

    @Test
    @DisplayName("GET /api/v1/portfolios/{id}/analytics requires authenticated owner session")
    void analyticsRequiresAuth() throws Exception {
        UUID pfId = UUID.randomUUID();
        mvc.perform(get("/api/v1/portfolios/" + pfId + "/analytics"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    @DisplayName("GET /api/v1/portfolios/{id}/analytics returns 200 PortfolioAnalyticsResponse")
    void analyticsSuccess() throws Exception {
        UUID pfId = UUID.randomUUID();
        PortfolioAnalyticsResponse response = new PortfolioAnalyticsResponse(
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-15"),
                false,
                "0.15",
                "0.05",
                PortfolioAnalyticsV1.DISCLOSURE_CODE,
                "0.02",
                Collections.emptyList(),
                List.of(new ConcentrationEntryResponse("FPT", "0.6")),
                List.of(new ConcentrationEntryResponse("Công nghệ", "0.6")),
                new RiskExposureResponse(25, "LOW", "1", null),
                new BenchmarkComparisonResponse("0.05", "0.02", "VNINDEX"),
                Instant.parse("2026-08-15T10:00:00Z"));

        given(analyticsService.getPortfolioAnalytics(eq(pfId), any(), any())).willReturn(response);

        mvc.perform(get("/api/v1/portfolios/" + pfId + "/analytics")
                        .session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnSinceInception").value("0.15"))
                .andExpect(jsonPath("$.returnMethodDisclosureCode").value("NET_CONTRIBUTED_CAPITAL_METHOD"))
                .andExpect(jsonPath("$.stockConcentration[0].key").value("FPT"))
                .andExpect(jsonPath("$.riskExposure.riskExposureLevel").value("LOW"))
                .andExpect(jsonPath("$.benchmark.benchmarkSymbol").value("VNINDEX"));
    }

    @Test
    @DisplayName("GET /api/v1/portfolios/{id}/analytics with unknown portfolio returns 404 PORTFOLIO_NOT_FOUND")
    void unknownPortfolioReturns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        given(analyticsService.getPortfolioAnalytics(eq(unknownId), any(), any()))
                .willThrow(new PortfolioNotFoundException(unknownId));

        mvc.perform(get("/api/v1/portfolios/" + unknownId + "/analytics")
                        .session(ownerSession()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.reasonCode").value("PORTFOLIO_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/portfolios/{id}/analytics exceeding max span returns 422 PERIOD_TOO_LONG")
    void periodTooLongReturns422() throws Exception {
        UUID pfId = UUID.randomUUID();
        given(analyticsService.getPortfolioAnalytics(eq(pfId), any(), any()))
                .willThrow(new PeriodTooLongException(1000L, 730L));

        mvc.perform(get("/api/v1/portfolios/" + pfId + "/analytics")
                        .session(ownerSession()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.reasonCode").value("PERIOD_TOO_LONG"));
    }

    private MockHttpSession ownerSession() throws Exception {
        var login = mvc.perform(post("/api/v1/auth/session")
                        .with(csrf())
                        .contentType("application/json")
                        .content(JSON.writeValueAsString(Map.of(
                                "username", OWNER_NAME,
                                "password", LOGIN_PROOF))))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }
}
