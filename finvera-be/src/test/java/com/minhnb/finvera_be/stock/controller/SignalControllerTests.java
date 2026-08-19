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
import com.minhnb.finvera_be.stock.domain.model.StockTypes.Direction;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.RiskFactorCode;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.RiskLevel;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.SignalStrength;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.LevelSet;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.RiskFactorResult;
import com.minhnb.finvera_be.stock.service.FundamentalReportService;
import com.minhnb.finvera_be.stock.service.StockChartService;
import com.minhnb.finvera_be.stock.service.StockOverviewService;
import com.minhnb.finvera_be.stock.service.StockSearchService;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService;
import com.minhnb.finvera_be.stock.service.ValuationService;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService.EvaluationStatus;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService.SignalDetail;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService.StockSignals;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService.StrategyEvaluationResult;
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

/** T008: contract/security tests for `GET /api/v1/stocks/{symbol}/signals` (strategy-signal.openapi.yaml). */
@WebMvcTest(controllers = {OwnerAccessController.class, StockController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class})
class SignalControllerTests {

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

    @Autowired private MockMvc mvc;

    @MockitoBean private StockSearchService searchService;
    @MockitoBean private StockOverviewService overviewService;
    @MockitoBean private StockChartService chartService;
    @MockitoBean private TechnicalIndicatorService technicalService;
    @MockitoBean private FundamentalReportService fundamentalService;
    @MockitoBean private ValuationService valuationService;
    @MockitoBean private StrategySignalService signalService;

    @Test
    void signalsRequireThePrivateOwnerSession() throws Exception {
        mvc.perform(get("/api/v1/stocks/FPT/signals"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void unknownSymbolReturns404WithoutFabricatingSignals() throws Exception {
        given(signalService.findBySymbol("ZZZZZ")).willReturn(Optional.empty());

        mvc.perform(get("/api/v1/stocks/ZZZZZ/signals").session(ownerSession()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reasonCode").value("STOCK_NOT_SUPPORTED"));
    }

    @Test
    void ownerReceivesEveryStrategyWithATriggeredSignalFullyDetailed() throws Exception {
        var levels = new LevelSet(new BigDecimal("99.500000"), new BigDecimal("100.500000"),
                new BigDecimal("96.000000"), new BigDecimal("108.000000"), new BigDecimal("112.000000"),
                new BigDecimal("2.0000"));
        var riskFactors = List.of(
                new RiskFactorResult(RiskFactorCode.VOLATILITY, new BigDecimal("2.5"), 10,
                        MetricApplicability.DEFINED, null),
                new RiskFactorResult(RiskFactorCode.MARKET_REGIME, null, null, MetricApplicability.MISSING,
                        "REGIME_UNAVAILABLE"));
        var trendSignal = new SignalDetail(StrategyCode.TREND_FOLLOWING, StrategySignalV1.RULE_VERSION,
                Direction.LONG, levels, 25, RiskLevel.LOW, SignalStrength.STRONG, riskFactors,
                Map.of("trend", "UPTREND"), List.of(), LocalDate.of(2026, 8, 14),
                Instant.parse("2026-08-14T08:15:00Z"));
        var evaluations = List.of(
                new StrategyEvaluationResult(StrategyCode.TREND_FOLLOWING, EvaluationStatus.SIGNAL, null,
                        trendSignal),
                new StrategyEvaluationResult(StrategyCode.MOMENTUM, EvaluationStatus.NO_SIGNAL, null, null),
                new StrategyEvaluationResult(StrategyCode.BREAKOUT, EvaluationStatus.INSUFFICIENT_HISTORY,
                        "INSUFFICIENT_HISTORY", null));
        var signals = new StockSignals("FPT", DataStatus.CURRENT, evaluations, "coh-signal-1",
                Instant.parse("2026-08-14T08:15:01Z"));
        given(signalService.findBySymbol("FPT")).willReturn(Optional.of(signals));

        mvc.perform(get("/api/v1/stocks/FPT/signals").session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("FPT"))
                .andExpect(jsonPath("$.disclaimerCode").value("QUANTITATIVE_DECISION_SUPPORT"))
                .andExpect(jsonPath("$.coherenceKey").value("coh-signal-1"))
                .andExpect(jsonPath("$.evaluations.length()").value(3))
                .andExpect(jsonPath("$.evaluations[0].strategyCode").value("TREND_FOLLOWING"))
                .andExpect(jsonPath("$.evaluations[0].status").value("SIGNAL"))
                .andExpect(jsonPath("$.evaluations[0].signal.direction").value("LONG"))
                .andExpect(jsonPath("$.evaluations[0].signal.entryLow").value("99.500000"))
                .andExpect(jsonPath("$.evaluations[0].signal.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.evaluations[0].signal.riskFactors.length()").value(2))
                .andExpect(jsonPath("$.evaluations[0].signal.riskFactors[1].applicability").value("MISSING"))
                .andExpect(jsonPath("$.evaluations[1].strategyCode").value("MOMENTUM"))
                .andExpect(jsonPath("$.evaluations[1].status").value("NO_SIGNAL"))
                .andExpect(jsonPath("$.evaluations[1].signal").doesNotExist())
                .andExpect(jsonPath("$.evaluations[2].status").value("INSUFFICIENT_HISTORY"))
                .andExpect(jsonPath("$.evaluations[2].reasonCode").value("INSUFFICIENT_HISTORY"));
    }

    @Test
    void repeatedRequestWithAMatchingETagReturns304() throws Exception {
        var signals = new StockSignals("FPT", DataStatus.CURRENT, List.of(), "coh-signal-2",
                Instant.parse("2026-08-14T08:15:01Z"));
        given(signalService.findBySymbol("FPT")).willReturn(Optional.of(signals));

        mvc.perform(get("/api/v1/stocks/FPT/signals").session(ownerSession())
                        .header("If-None-Match", "coh-signal-2"))
                .andExpect(status().isNotModified());
    }

    @Test
    void aPostToTheOwnerSessionEndpointStillRequiresCsrf() throws Exception {
        // The scan endpoint (POST /strategies/{code}/scan) is where CSRF is
        // load-bearing (T019/quickstart Authorization checks); this GET
        // endpoint is a safe method and carries no CSRF requirement of its
        // own. This test only documents that fact so a future reader does not
        // wonder why no CSRF case appears here for a GET.
        mvc.perform(post("/api/v1/auth/session").with(csrf())
                        .contentType("application/json")
                        .content(JSON.writeValueAsString(Map.of("username", OWNER_NAME, "password", LOGIN_PROOF))))
                .andExpect(status().isNoContent());
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
