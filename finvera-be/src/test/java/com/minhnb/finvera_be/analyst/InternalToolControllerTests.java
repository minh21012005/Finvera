package com.minhnb.finvera_be.analyst;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.analyst.controller.InternalToolController;
import com.minhnb.finvera_be.analyst.dto.ToolResponseDtos.MarketOverviewToolResponse;
import com.minhnb.finvera_be.analyst.dto.ToolResponseDtos.StockSummaryToolResponse;
import com.minhnb.finvera_be.analyst.service.ToolDelegateService;
import com.minhnb.finvera_be.auth.config.OwnerProperties;
import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.portfolio.service.OwnerScopedAccess;
import com.minhnb.finvera_be.research.config.InternalApiKeyFilter;
import com.minhnb.finvera_be.research.config.ResearchProperties;
import com.minhnb.finvera_be.research.dto.PassageResponse;
import com.minhnb.finvera_be.research.dto.RetrieveResponse;
import com.minhnb.finvera_be.research.dto.SourceType;
import com.minhnb.finvera_be.research.service.RetrievalService;
import com.minhnb.finvera_be.shared.api.CorrelationIdFilter;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
class InternalToolControllerTests {

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
        mockMvc.perform(get("/internal/v1/tools/market/overview").param("ownerId", OWNER_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reasonCode").value("UNAUTHORIZED"));
    }

    @Test
    void invalidInternalApiKey_returns401() throws Exception {
        mockMvc.perform(get("/internal/v1/tools/market/overview")
                        .param("ownerId", OWNER_ID)
                        .header("X-Internal-Api-Key", "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reasonCode").value("UNAUTHORIZED"));
    }

    @Test
    void missingOwnerId_returns404() throws Exception {
        // U-2 (orchestration-v1): ownerId is required on every tool call, not merely
        // accepted-but-ignored — a caller that omits it entirely is rejected exactly
        // like an invalid argument, never silently served using ambient config alone.
        mockMvc.perform(get("/internal/v1/tools/market/overview")
                        .header("X-Internal-Api-Key", VALID_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void mismatchedOwnerId_returns404() throws Exception {
        mockMvc.perform(get("/internal/v1/tools/market/overview")
                        .param("ownerId", OTHER_OWNER_ID)
                        .header("X-Internal-Api-Key", VALID_KEY))
                .andExpect(status().isNotFound());
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
                        .param("ownerId", OWNER_ID)
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
                        .param("ownerId", OWNER_ID)
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

        mockMvc.perform(post("/internal/v1/tools/screener/executions")
                        .param("ownerId", OWNER_ID)
                        .header("X-Internal-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundamental\":{\"peMax\":\"10\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMatches").value(1))
                .andExpect(jsonPath("$.matches[0].symbol").value("HPG"));
    }

    @Test
    void validInternalApiKey_researchRetrieve_returns200_andDelegatesToRetrievalService() throws Exception {
        // Foundational fix regression test: the Research/RAG tool must reuse Feature
        // 006's own RetrievalService (real Postgres-resolved excerpt text), not read
        // Qdrant/finvera-ai's own retrieval module directly, since that path can never
        // carry chunk content_text.
        when(retrievalService.retrievePassages(any())).thenReturn(new RetrieveResponse(
                List.of(new PassageResponse(
                        UUID.randomUUID(),
                        SourceType.DOCUMENT,
                        UUID.randomUUID(),
                        "Báo cáo thường niên HPG 2025",
                        "Page 12",
                        "HPG Investor Relations",
                        LocalDate.of(2025, 12, 31),
                        "Kế hoạch doanh thu 2026 đạt 65.000 tỷ VND",
                        0.9)),
                Instant.parse("2026-08-20T10:00:00Z")));

        mockMvc.perform(post("/internal/v1/tools/research/retrieve")
                        .param("ownerId", OWNER_ID)
                        .header("X-Internal-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"kế hoạch doanh thu\",\"filters\":{\"symbol\":\"HPG\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passages[0].sourceTitle").value("Báo cáo thường niên HPG 2025"))
                .andExpect(jsonPath("$.passages[0].excerpt").value("Kế hoạch doanh thu 2026 đạt 65.000 tỷ VND"));
    }
}
