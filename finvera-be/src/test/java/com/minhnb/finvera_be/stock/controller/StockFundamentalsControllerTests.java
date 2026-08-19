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
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.ValuationLabel;
import com.minhnb.finvera_be.stock.service.FundamentalReportService;
import com.minhnb.finvera_be.stock.service.FundamentalReportService.StockFundamentals;
import com.minhnb.finvera_be.stock.service.ValuationService;
import com.minhnb.finvera_be.stock.service.ValuationService.StockValuation;
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

/** Contract-first tests for FR-007, FR-008, FR-009, FR-010 endpoints. T046. */
@WebMvcTest(controllers = {OwnerAccessController.class, StockController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class})
class StockFundamentalsControllerTests {

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

    // All existing services need to be mocked for the WebMvcTest context
    @MockitoBean private com.minhnb.finvera_be.stock.service.StockSearchService searchService;
    @MockitoBean private com.minhnb.finvera_be.stock.service.StockOverviewService overviewService;
    @MockitoBean private com.minhnb.finvera_be.stock.service.StockChartService chartService;
    @MockitoBean private com.minhnb.finvera_be.stock.service.TechnicalIndicatorService technicalService;
    @MockitoBean private FundamentalReportService fundamentalService;
    @MockitoBean private ValuationService valuationService;
    @MockitoBean private com.minhnb.finvera_be.stock.service.strategy.StrategySignalService signalService;

    // ── Security ────────────────────────────────────────────────────────────────

    @Test
    void fundamentalsRequiresPrivateOwnerSession() throws Exception {
        mvc.perform(get("/api/v1/stocks/FPT/fundamentals"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void valuationRequiresPrivateOwnerSession() throws Exception {
        mvc.perform(get("/api/v1/stocks/FPT/valuation"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    // ── GET /fundamentals happy path ─────────────────────────────────────────────

    @Test
    void ownerReceivesFundamentalsWithPeriodAndMetrics() throws Exception {
        var fundamentals = buildCompleteFundamentals();
        given(fundamentalService.findBySymbol("FPT")).willReturn(Optional.of(fundamentals));

        mvc.perform(get("/api/v1/stocks/FPT/fundamentals").session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.periodType").value("QUARTER"))
                .andExpect(jsonPath("$.period.fiscalYear").value(2026))
                .andExpect(jsonPath("$.period.fiscalQuarter").value(2))
                .andExpect(jsonPath("$.period.reportKind").value("CONSOLIDATED"))
                .andExpect(jsonPath("$.period.auditStatus").value("REVIEWED"))
                .andExpect(jsonPath("$.period.currency").value("VND"))
                .andExpect(jsonPath("$.period.restated").value(false))
                .andExpect(jsonPath("$.metrics.length()").value(2))
                .andExpect(jsonPath("$.metrics[0].metricCode").value("REVENUE"))
                .andExpect(jsonPath("$.metrics[0].applicability").value("DEFINED"))
                .andExpect(jsonPath("$.metrics[0].value").value("16250000000000.00")) // display precision 2
                .andExpect(jsonPath("$.metrics[1].metricCode").value("EPS"))
                .andExpect(jsonPath("$.metrics[1].applicability").value("DEFINED"));
    }

    @Test
    void fundamentalsUnknownSymbolReturns404() throws Exception {
        given(fundamentalService.findBySymbol("ZZZZZ")).willReturn(Optional.empty());

        mvc.perform(get("/api/v1/stocks/ZZZZZ/fundamentals").session(ownerSession()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reasonCode").value("STOCK_NOT_SUPPORTED"));
    }

    // ── GET /valuation happy path ────────────────────────────────────────────────

    @Test
    void ownerReceivesPublishedValuationWithAllOrNothingFields() throws Exception {
        var valuation = buildPublishedValuation();
        given(valuationService.findBySymbol("FPT")).willReturn(Optional.of(valuation));

        mvc.perform(get("/api/v1/stocks/FPT/valuation").session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleVersion").value("valuation-v1"))
                .andExpect(jsonPath("$.disclaimerCode").value("QUANTITATIVE_DECISION_SUPPORT"))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.classification").value("FAIR_VALUED"))
                .andExpect(jsonPath("$.score").exists())
                .andExpect(jsonPath("$.displayedScore").isNumber())
                .andExpect(jsonPath("$.confidence").isNumber())
                .andExpect(jsonPath("$.basis.usedOwnHistory").value(true))
                .andExpect(jsonPath("$.basis.usedSector").value(false))
                .andExpect(jsonPath("$.metrics.length()").value(5))
                .andExpect(jsonPath("$.metrics[0].metricCode").value("PE"))
                .andExpect(jsonPath("$.metrics[0].applicability").value("DEFINED"));
    }

    @Test
    void withheldValuationHasNullClassificationScoreAndConfidence() throws Exception {
        var valuation = buildWithheldValuation();
        given(valuationService.findBySymbol("FPT")).willReturn(Optional.of(valuation));

        mvc.perform(get("/api/v1/stocks/FPT/valuation").session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.classification").isEmpty())
                .andExpect(jsonPath("$.score").isEmpty())
                .andExpect(jsonPath("$.displayedScore").isEmpty())
                .andExpect(jsonPath("$.confidence").isEmpty())
                .andExpect(jsonPath("$.meta.reasonCodes[0]").value("NO_COMPARISON_BASIS"));
    }

    @Test
    void valuationUnknownSymbolReturns404() throws Exception {
        given(valuationService.findBySymbol("ZZZZZ")).willReturn(Optional.empty());

        mvc.perform(get("/api/v1/stocks/ZZZZZ/valuation").session(ownerSession()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reasonCode").value("STOCK_NOT_SUPPORTED"));
    }

    // ── Test fixtures ────────────────────────────────────────────────────────────

    private StockFundamentals buildCompleteFundamentals() {
        return new StockFundamentals(
                "FPT", "QUARTER", 2026, 2,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                "CONSOLIDATED", "REVIEWED", "VND",
                false,
                "2026-Q2",
                List.of(
                        new FundamentalReportService.FundamentalMetric(
                                "REVENUE", new BigDecimal("16250000000000.000000"),
                                "VND", 2, MetricApplicability.DEFINED, null),
                        new FundamentalReportService.FundamentalMetric(
                                "EPS", new BigDecimal("1300.000000"),
                                "VND", 0, MetricApplicability.DEFINED, null)
                ),
                DataStatus.CURRENT, List.of(), LocalDate.of(2026, 8, 14),
                Instant.parse("2026-08-14T10:00:00Z"), "coh-fundamentals-1", null);
    }

    private StockValuation buildPublishedValuation() {
        return new StockValuation(
                "FPT", RULE_VERSION,
                true,                                          // published
                ValuationLabel.FAIR_VALUED,
                new BigDecimal("48.250000000000"),             // unrounded score
                48,                                            // displayedScore
                72,                                            // confidence
                true,                                          // usedOwnHistory
                false,                                         // usedSector
                null, null, null, null, 600,
                List.of(
                        new ValuationService.ValuationMetric("PE", new BigDecimal("15.123456789012"),
                                MetricApplicability.DEFINED, new BigDecimal("48.250000"), null,
                                new BigDecimal("0.571428571429"), null),
                        new ValuationService.ValuationMetric("PB", new BigDecimal("2.415000000000"),
                                MetricApplicability.DEFINED, new BigDecimal("40.000000"), null,
                                new BigDecimal("0.428571428571"), null),
                        new ValuationService.ValuationMetric("EV_EBITDA", null,
                                MetricApplicability.MISSING, null, null, null, "MISSING_INPUT"),
                        new ValuationService.ValuationMetric("PEG", null,
                                MetricApplicability.MISSING, null, null, null, "MISSING_INPUT"),
                        new ValuationService.ValuationMetric("DIVIDEND_YIELD", new BigDecimal("2.890000000000"),
                                MetricApplicability.DEFINED, null, null, null, null)
                ),
                DataStatus.CURRENT, List.of(), LocalDate.of(2026, 8, 14),
                Instant.parse("2026-08-14T10:00:00Z"), "coh-valuation-1");
    }

    private StockValuation buildWithheldValuation() {
        return new StockValuation(
                "FPT", RULE_VERSION,
                false,             // not published
                null, null, null, null,
                false, false, null, null, null, null, 0,
                List.of(),
                DataStatus.UNAVAILABLE, List.of("NO_COMPARISON_BASIS"),
                LocalDate.of(2026, 8, 14),
                Instant.parse("2026-08-14T10:00:00Z"), "coh-valuation-withheld");
    }

    private static final String RULE_VERSION = "valuation-v1";

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
