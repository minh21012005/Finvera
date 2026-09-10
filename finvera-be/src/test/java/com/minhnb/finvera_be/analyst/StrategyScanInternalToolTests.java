package com.minhnb.finvera_be.analyst;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.analyst.controller.InternalToolController;
import com.minhnb.finvera_be.analyst.service.ToolDelegateService;
import com.minhnb.finvera_be.auth.config.OwnerProperties;
import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.portfolio.service.OwnerScopedAccess;
import com.minhnb.finvera_be.research.config.InternalApiKeyFilter;
import com.minhnb.finvera_be.research.config.ResearchProperties;
import com.minhnb.finvera_be.research.service.RetrievalService;
import com.minhnb.finvera_be.shared.api.CorrelationIdFilter;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode;
import com.minhnb.finvera_be.stock.dto.ScanResponse;
import com.minhnb.finvera_be.stock.dto.ScanResponse.ScanMatchResponse;
import com.minhnb.finvera_be.stock.dto.StockSignalsResponse.SignalResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {InternalToolController.class})
@Import({
        OwnerSecurityConfiguration.class,
        OwnerSessionService.class,
        InternalApiKeyFilter.class,
        CorrelationIdFilter.class,
        ProblemDetailsAdvice.class,
        OwnerScopedAccess.class
})
@EnableConfigurationProperties({ResearchProperties.class, OwnerProperties.class})
class StrategyScanInternalToolTests {

    private static final String VALID_KEY = "test-internal-api-key";
    private static final String OWNER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String OTHER_OWNER_ID = "11111111-1111-1111-1111-111111111111";

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("finvera.security.owner.id", () -> OWNER_ID);
        registry.add("finvera.security.owner.username", () -> "owner-test");
        registry.add("finvera.security.owner.password-hash", () -> "$2a$04$test");
        registry.add("finvera.research.internal-api-key", () -> VALID_KEY);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ToolDelegateService toolDelegateService;

    @MockitoBean
    private RetrievalService retrievalService;

    @Test
    void missingInternalApiKey_returns401() throws Exception {
        mockMvc.perform(post("/internal/v1/tools/strategies/scan")
                        .param("ownerId", OWNER_ID)
                        .param("strategyCode", "BREAKOUT"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reasonCode").value("UNAUTHORIZED"));
    }

    @Test
    void invalidInternalApiKey_returns401() throws Exception {
        mockMvc.perform(post("/internal/v1/tools/strategies/scan")
                        .param("ownerId", OWNER_ID)
                        .param("strategyCode", "BREAKOUT")
                        .header("X-Internal-Api-Key", "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reasonCode").value("UNAUTHORIZED"));
    }

    @Test
    void missingOwnerId_returns404() throws Exception {
        mockMvc.perform(post("/internal/v1/tools/strategies/scan")
                        .param("strategyCode", "BREAKOUT")
                        .header("X-Internal-Api-Key", VALID_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void mismatchedOwnerId_returns404() throws Exception {
        mockMvc.perform(post("/internal/v1/tools/strategies/scan")
                        .param("ownerId", OTHER_OWNER_ID)
                        .param("strategyCode", "BREAKOUT")
                        .header("X-Internal-Api-Key", VALID_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void validInternalApiKey_strategyScan_returns200_withScanResponse() throws Exception {
        SignalResponse signal = new SignalResponse(
                "BREAKOUT",
                "v1.0",
                "BULLISH",
                "28000.0",
                "28500.0",
                "27000.0",
                "30000.0",
                "32000.0",
                "2.0",
                25,
                "LOW",
                "STRONG",
                List.of(),
                Map.of("RESISTANCE_BREAK", "Price closed above 50-day high"),
                List.of(),
                "2026-09-09",
                Instant.now().toString());

        ScanMatchResponse match = new ScanMatchResponse("HPG", "Tập đoàn Hòa Phát", "HOSE", signal);
        ScanResponse response = new ScanResponse(
                "BREAKOUT",
                List.of(match),
                1,
                5,
                0,
                0,
                Instant.now().toString());

        when(toolDelegateService.scanStrategy(eq(StrategyCode.BREAKOUT), eq(5))).thenReturn(response);

        mockMvc.perform(post("/internal/v1/tools/strategies/scan")
                        .param("ownerId", OWNER_ID)
                        .param("strategyCode", "BREAKOUT")
                        .param("limit", "5")
                        .header("X-Internal-Api-Key", VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyCode").value("BREAKOUT"))
                .andExpect(jsonPath("$.totalMatchCount").value(1))
                .andExpect(jsonPath("$.matches[0].symbol").value("HPG"))
                .andExpect(jsonPath("$.matches[0].companyName").value("Tập đoàn Hòa Phát"))
                .andExpect(jsonPath("$.matches[0].signal.direction").value("BULLISH"))
                .andExpect(jsonPath("$.matches[0].signal.entryLow").value("28000.0"))
                .andExpect(jsonPath("$.matches[0].signal.stopLoss").value("27000.0"))
                .andExpect(jsonPath("$.matches[0].signal.signalStrength").value("STRONG"));
    }
}
