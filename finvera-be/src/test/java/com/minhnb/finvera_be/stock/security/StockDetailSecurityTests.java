package com.minhnb.finvera_be.stock.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.controller.OwnerAccessController;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.stock.controller.StockController;
import com.minhnb.finvera_be.stock.service.FundamentalReportService;
import com.minhnb.finvera_be.stock.service.StockChartService;
import com.minhnb.finvera_be.stock.service.StockOverviewService;
import com.minhnb.finvera_be.stock.service.StockSearchService;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService;
import com.minhnb.finvera_be.stock.service.ValuationService;
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
 * T069 [SEC-001-SEC-004, SC-008]
 * Security verification: all stock endpoints deny unauthenticated / non-owner requests
 * with standard RFC 9457 problem details.
 */
@WebMvcTest(controllers = {OwnerAccessController.class, StockController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class})
class StockDetailSecurityTests {

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

    @Test
    void unauthenticatedRequestsAreDeniedAcrossAllStockEndpoints() throws Exception {
        String[] endpoints = {
                "/api/v1/stocks",
                "/api/v1/stocks/FPT",
                "/api/v1/stocks/FPT/chart",
                "/api/v1/stocks/FPT/technical",
                "/api/v1/stocks/FPT/fundamentals",
                "/api/v1/stocks/FPT/valuation"
        };

        for (String endpoint : endpoints) {
            mvc.perform(get(endpoint))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("Authentication required"));
        }
    }

    @Test
    void nonMarketEndpointsDoNotExposeTradingOrCashOrOrderOperations() throws Exception {
        String[] forbiddenEndpoints = {
                "/api/v1/orders",
                "/api/v1/trading",
                "/api/v1/cash",
                "/api/v1/portfolio/trade"
        };

        for (String endpoint : forbiddenEndpoints) {
            mvc.perform(post(endpoint))
                    .andExpect(status().isForbidden());
        }
    }
}
