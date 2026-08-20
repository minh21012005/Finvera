package com.minhnb.finvera_be.analyst;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.analyst.controller.InternalToolController;
import com.minhnb.finvera_be.analyst.dto.ToolResponseDtos.MarketOverviewToolResponse;
import com.minhnb.finvera_be.analyst.dto.ToolResponseDtos.StockSummaryToolResponse;
import com.minhnb.finvera_be.analyst.service.ToolDelegateService;
import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.research.config.InternalApiKeyFilter;
import com.minhnb.finvera_be.research.config.ResearchProperties;
import com.minhnb.finvera_be.shared.api.CorrelationIdFilter;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice;
import java.time.Instant;
import java.util.Collections;
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
        ProblemDetailsAdvice.class
})
@EnableConfigurationProperties(ResearchProperties.class)
class InternalToolControllerTests {

    private static final String VALID_KEY = "test-internal-api-key";

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("finvera.security.owner.id", () -> "00000000-0000-0000-0000-000000000001");
        registry.add("finvera.security.owner.username", () -> "owner-test");
        registry.add("finvera.security.owner.password-hash", () -> "$2a$04$test");
        registry.add("finvera.research.internal-api-key", () -> VALID_KEY);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ToolDelegateService toolDelegateService;

    @Test
    void missingInternalApiKey_returns401() throws Exception {
        mockMvc.perform(get("/internal/v1/tools/market/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reasonCode").value("UNAUTHORIZED"));
    }

    @Test
    void invalidInternalApiKey_returns401() throws Exception {
        mockMvc.perform(get("/internal/v1/tools/market/overview")
                        .header("X-Internal-Api-Key", "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reasonCode").value("UNAUTHORIZED"));
    }

    @Test
    void validInternalApiKey_marketOverview_returns200() throws Exception {
        when(toolDelegateService.getMarketOverview()).thenReturn(new MarketOverviewToolResponse(
                "1250.5",
                "1.2",
                250,
                110,
                50,
                Instant.parse("2026-08-20T10:00:00Z"),
                Collections.emptyMap()));

        mockMvc.perform(get("/internal/v1/tools/market/overview")
                        .header("X-Internal-Api-Key", VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vnIndexValue").value("1250.5"))
                .andExpect(jsonPath("$.advancers").value(250));
    }

    @Test
    void validInternalApiKey_stockSummary_returns200() throws Exception {
        when(toolDelegateService.getStockSummary("HPG")).thenReturn(new StockSummaryToolResponse(
                "HPG",
                "Tập đoàn Hòa Phát",
                "28500",
                "2.1",
                15000000L,
                Instant.parse("2026-08-20T10:00:00Z"),
                Collections.emptyMap()));

        mockMvc.perform(get("/internal/v1/tools/stocks/HPG")
                        .header("X-Internal-Api-Key", VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("HPG"))
                .andExpect(jsonPath("$.price").value("28500"));
    }

    @Test
    void validInternalApiKey_screenerExecutions_returns200() throws Exception {
        when(toolDelegateService.executeScreener(any())).thenReturn(
                new com.minhnb.finvera_be.analyst.dto.ToolResponseDtos.ScreenerExecutionToolResponse(
                        java.util.List.of(java.util.Map.of("symbol", "HPG", "companyName", "Hòa Phát")),
                        1,
                        Instant.parse("2026-08-20T10:00:00Z")));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/v1/tools/screener/executions")
                        .header("X-Internal-Api-Key", VALID_KEY)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"fundamental\":{\"peMax\":\"10\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMatches").value(1))
                .andExpect(jsonPath("$.matches[0].symbol").value("HPG"));
    }
}
