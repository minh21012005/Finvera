package com.minhnb.finvera_be.stock.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.controller.OwnerAccessController;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.stock.controller.StockController;
import com.minhnb.finvera_be.stock.controller.StrategyScanController;
import com.minhnb.finvera_be.stock.service.FundamentalReportService;
import com.minhnb.finvera_be.stock.service.StockChartService;
import com.minhnb.finvera_be.stock.service.StockOverviewService;
import com.minhnb.finvera_be.stock.service.StockSearchService;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService;
import com.minhnb.finvera_be.stock.service.ValuationService;
import com.minhnb.finvera_be.stock.service.strategy.StrategyScanService;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T030 [SEC-001, SEC-002]. Mirrors {@code ScreenerSecurityTests}' pattern:
 * this private single-owner deployment (ADR-0005) has no second registered
 * identity to test as "non-owner" — every non-owner request is, by
 * construction, an unauthenticated request (SEC-001). Covers both Feature
 * 004 endpoints together, including the scan endpoint's CSRF requirement
 * verified from the first implementation this time.
 */
@WebMvcTest(controllers = {OwnerAccessController.class, StockController.class, StrategyScanController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class})
class StrategySignalSecurityTests {

    private static final String OWNER_NAME = "owner-" + UUID.randomUUID();
    private static final String LOGIN_PROOF = UUID.randomUUID().toString();
    private static final String LOGIN_PROOF_HASH = new BCryptPasswordEncoder(4).encode(LOGIN_PROOF);

    @DynamicPropertySource
    static void ownerProperties(DynamicPropertyRegistry registry) {
        registry.add("finvera.security.owner.id", () -> "00000000-0000-0000-0000-000000000001");
        registry.add("finvera.security.owner.username", () -> OWNER_NAME);
        registry.add("finvera.security.owner.password-hash", () -> LOGIN_PROOF_HASH);
    }

    @Autowired private MockMvc mvc;

    @MockitoBean private StockSearchService searchService;
    @MockitoBean private StockOverviewService overviewService;
    @MockitoBean private StockChartService chartService;
    @MockitoBean private TechnicalIndicatorService technicalService;
    @MockitoBean private FundamentalReportService fundamentalService;
    @MockitoBean private ValuationService valuationService;
    @MockitoBean private StrategySignalService signalService;
    @MockitoBean private StrategyScanService scanService;

    @Test
    void unauthenticatedRequestToSignalsIsDeniedWithoutInvokingTheService() throws Exception {
        mvc.perform(get("/api/v1/stocks/FPT/signals"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication required"));

        org.mockito.Mockito.verifyNoInteractions(signalService);
    }

    @Test
    void unauthenticatedRequestToScanIsDeniedWithoutInvokingTheService() throws Exception {
        mvc.perform(post("/api/v1/strategies/TREND_FOLLOWING/scan").with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication required"));

        org.mockito.Mockito.verifyNoInteractions(scanService);
    }

    @Test
    void scanWithAnAuthenticatedSessionButNoCsrfTokenIsDeniedWithoutInvokingTheService() throws Exception {
        var login = mvc.perform(post("/api/v1/auth/session").with(csrf())
                        .contentType("application/json")
                        .content("{\"username\":\"" + OWNER_NAME + "\",\"password\":\"" + LOGIN_PROOF + "\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(post("/api/v1/strategies/TREND_FOLLOWING/scan").session(session)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());

        org.mockito.Mockito.verifyNoInteractions(scanService);
    }
}
