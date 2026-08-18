package com.minhnb.finvera_be.stock.controller;

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
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.AdjustmentStatus;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorCode;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorComponent;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.Unit;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.ComponentResult;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.IndicatorResult;
import com.minhnb.finvera_be.stock.service.StockChartService;
import com.minhnb.finvera_be.stock.service.StockOverviewService;
import com.minhnb.finvera_be.stock.service.StockSearchService;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService.StockTechnical;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

/** Contract-first tests for FR-005, FR-006. */
@WebMvcTest(controllers = {OwnerAccessController.class, StockController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class})
class StockTechnicalControllerTests {

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
    private StockSearchService searchService;

    @MockitoBean
    private StockOverviewService overviewService;

    @MockitoBean
    private StockChartService chartService;

    @MockitoBean
    private TechnicalIndicatorService technicalService;

    @MockitoBean
    private com.minhnb.finvera_be.stock.service.FundamentalReportService fundamentalService;

    @MockitoBean
    private com.minhnb.finvera_be.stock.service.ValuationService valuationService;

    @Test
    void technicalRequiresThePrivateOwnerSession() throws Exception {
        mvc.perform(get("/api/v1/stocks/FPT/technical"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void ownerReceivesEveryIndicatorWithApplicabilityAndTheDecisionSupportDisclaimer() throws Exception {
        var ma20 = new IndicatorResult(IndicatorCode.MA20, MetricApplicability.DEFINED, 20, 20,
                LocalDate.of(2026, 7, 17), LocalDate.of(2026, 8, 14), 20, "a".repeat(64), null,
                List.of(new ComponentResult(IndicatorComponent.VALUE, new BigDecimal("41491.754764850000"),
                        Unit.VND, 2, MetricApplicability.DEFINED, null)));
        var ma200 = new IndicatorResult(IndicatorCode.MA200, MetricApplicability.MISSING, 200, 199, null, null, 0,
                null, "INSUFFICIENT_HISTORY", List.of());
        var technical = new StockTechnical(TechnicalIndicatorsV1.RULE_VERSION, AdjustmentStatus.ADJUSTED,
                List.of(ma20, ma200), DataStatus.CURRENT, List.of(), LocalDate.of(2026, 8, 14),
                Instant.parse("2026-08-17T07:15:00Z"), "coh-technical-1");
        given(technicalService.findBySymbol("FPT")).willReturn(Optional.of(technical));

        mvc.perform(get("/api/v1/stocks/FPT/technical").session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleVersion").value("technical-indicators-v1"))
                .andExpect(jsonPath("$.disclaimerCode").value("QUANTITATIVE_DECISION_SUPPORT"))
                .andExpect(jsonPath("$.indicators.length()").value(2))
                .andExpect(jsonPath("$.indicators[0].indicatorCode").value("MA20"))
                .andExpect(jsonPath("$.indicators[0].applicability").value("DEFINED"))
                .andExpect(jsonPath("$.indicators[0].components[0].value").value("41491.75"))
                .andExpect(jsonPath("$.indicators[1].indicatorCode").value("MA200"))
                .andExpect(jsonPath("$.indicators[1].applicability").value("MISSING"))
                .andExpect(jsonPath("$.indicators[1].requiredBars").value(200))
                .andExpect(jsonPath("$.indicators[1].availableBars").value(199))
                .andExpect(jsonPath("$.indicators[1].reasonCode").value("INSUFFICIENT_HISTORY"))
                .andExpect(jsonPath("$.indicators[1].components.length()").value(0));
    }

    @Test
    void unknownSymbolReturns404WithoutFabricatingIndicators() throws Exception {
        given(technicalService.findBySymbol("ZZZZZ")).willReturn(Optional.empty());

        mvc.perform(get("/api/v1/stocks/ZZZZZ/technical").session(ownerSession()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reasonCode").value("STOCK_NOT_SUPPORTED"));
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
