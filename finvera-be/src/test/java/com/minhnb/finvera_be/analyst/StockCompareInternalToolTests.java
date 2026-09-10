package com.minhnb.finvera_be.analyst;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.analyst.controller.InternalToolController;
import com.minhnb.finvera_be.analyst.dto.ToolResponseDtos.StockComparisonItemDto;
import com.minhnb.finvera_be.analyst.dto.ToolResponseDtos.StockComparisonToolResponse;
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
import java.time.Instant;
import java.util.List;
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
class StockCompareInternalToolTests {

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
        mockMvc.perform(post("/internal/v1/tools/stocks/compare")
                        .param("ownerId", OWNER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbols\":[\"SSI\",\"VND\"]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reasonCode").value("UNAUTHORIZED"));
    }

    @Test
    void invalidInternalApiKey_returns401() throws Exception {
        mockMvc.perform(post("/internal/v1/tools/stocks/compare")
                        .param("ownerId", OWNER_ID)
                        .header("X-Internal-Api-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbols\":[\"SSI\",\"VND\"]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reasonCode").value("UNAUTHORIZED"));
    }

    @Test
    void missingOwnerId_returns404() throws Exception {
        mockMvc.perform(post("/internal/v1/tools/stocks/compare")
                        .header("X-Internal-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbols\":[\"SSI\",\"VND\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void mismatchedOwnerId_returns404() throws Exception {
        mockMvc.perform(post("/internal/v1/tools/stocks/compare")
                        .param("ownerId", OTHER_OWNER_ID)
                        .header("X-Internal-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbols\":[\"SSI\",\"VND\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void validInternalApiKey_compareStocks_returns200_withComparisonItems() throws Exception {
        StockComparisonItemDto ssi = new StockComparisonItemDto(
                "SSI",
                "CTCP Chứng khoán SSI",
                "HOSE",
                "Dịch vụ tài chính",
                "32500",
                "1.2",
                12000000L,
                "48000000000000",
                "18.5",
                "1.8",
                "FAIR",
                "52.0",
                "14.5",
                "4.2",
                "1850",
                "1900",
                "22.5",
                "18.0",
                "58.2",
                "TRACKING",
                "MOMENTUM (BULLISH)",
                "MODERATE",
                "LOW",
                "CURRENT",
                List.of());

        StockComparisonItemDto vnd = new StockComparisonItemDto(
                "VND",
                "CTCP Chứng khoán VNDIRECT",
                "HOSE",
                "Dịch vụ tài chính",
                "18200",
                "-0.5",
                8000000L,
                "22000000000000",
                "14.2",
                "1.3",
                "ATTRACTIVE",
                "65.0",
                "11.8",
                "3.5",
                "1250",
                "1300",
                "15.0",
                "10.5",
                "46.5",
                "TRACKING",
                null,
                null,
                "MEDIUM",
                "CURRENT",
                List.of());

        StockComparisonToolResponse response = new StockComparisonToolResponse(List.of(ssi, vnd), Instant.now());
        when(toolDelegateService.compareStocks(anyList())).thenReturn(response);

        mockMvc.perform(post("/internal/v1/tools/stocks/compare")
                        .param("ownerId", OWNER_ID)
                        .header("X-Internal-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbols\":[\"SSI\",\"VND\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].symbol").value("SSI"))
                .andExpect(jsonPath("$.items[0].price").value("32500"))
                .andExpect(jsonPath("$.items[0].pe").value("18.5"))
                .andExpect(jsonPath("$.items[0].roe").value("14.5"))
                .andExpect(jsonPath("$.items[1].symbol").value("VND"))
                .andExpect(jsonPath("$.items[1].price").value("18200"))
                .andExpect(jsonPath("$.items[1].valuationClassification").value("ATTRACTIVE"));
    }
}
