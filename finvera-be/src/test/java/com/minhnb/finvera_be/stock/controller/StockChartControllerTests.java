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
import com.minhnb.finvera_be.stock.domain.chart.StockChartAssembler.ChartBar;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.AdjustmentStatus;
import com.minhnb.finvera_be.stock.service.StockChartService;
import com.minhnb.finvera_be.stock.service.StockChartService.StockChart;
import com.minhnb.finvera_be.stock.service.StockOverviewService;
import com.minhnb.finvera_be.stock.service.StockSearchService;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService;
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

/** Contract-first tests for FR-003, DATA-008. */
@WebMvcTest(controllers = {OwnerAccessController.class, StockController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class})
class StockChartControllerTests {

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

    @MockitoBean
    private com.minhnb.finvera_be.stock.service.strategy.StrategySignalService signalService;

    @Test
    void chartRequiresThePrivateOwnerSession() throws Exception {
        mvc.perform(get("/api/v1/stocks/FPT/chart"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void ownerReceivesAnAscendingSeriesWithOneAdjustmentStatusForTheWholeWindow() throws Exception {
        var bars = List.of(
                new ChartBar(LocalDate.of(2026, 8, 13), new BigDecimal("122000.000000"),
                        new BigDecimal("123000.000000"), new BigDecimal("121500.000000"),
                        new BigDecimal("122500.000000"), 2_000_000L),
                new ChartBar(LocalDate.of(2026, 8, 14), new BigDecimal("122500.000000"),
                        new BigDecimal("123800.000000"), new BigDecimal("122300.000000"),
                        new BigDecimal("123600.000000"), 2_270_000L));
        var chart = new StockChart("1M", AdjustmentStatus.ADJUSTED, bars, DataStatus.CURRENT, null,
                Instant.parse("2026-08-17T07:15:00Z"), "coh-chart-1");
        given(chartService.findBySymbol("FPT", "1M")).willReturn(Optional.of(chart));

        mvc.perform(get("/api/v1/stocks/FPT/chart").param("window", "1M").session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adjustmentStatus").value("ADJUSTED"))
                .andExpect(jsonPath("$.bars.length()").value(2))
                .andExpect(jsonPath("$.bars[0].tradingDate").value("2026-08-13"))
                .andExpect(jsonPath("$.bars[1].tradingDate").value("2026-08-14"))
                .andExpect(jsonPath("$.bars[1].close").value("123600.000000"));
    }

    @Test
    void chartUnavailableWhileOverviewStaysUsableIsAnExplicitStateNotAnError() throws Exception {
        var chart = new StockChart("1M", com.minhnb.finvera_be.stock.domain.model.StockTypes.AdjustmentStatus.UNKNOWN,
                List.of(), DataStatus.UNAVAILABLE, "PROVIDER_UNAVAILABLE", Instant.parse("2026-08-17T07:15:00Z"),
                "coh-chart-empty");
        given(chartService.findBySymbol("FPT", "1M")).willReturn(Optional.of(chart));

        mvc.perform(get("/api/v1/stocks/FPT/chart").param("window", "1M").session(ownerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.dataStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.bars.length()").value(0));
    }

    @Test
    void unknownSymbolReturns404WithoutFabricatingAChart() throws Exception {
        given(chartService.findBySymbol("ZZZZZ", "1Y")).willReturn(Optional.empty());

        mvc.perform(get("/api/v1/stocks/ZZZZZ/chart").session(ownerSession()))
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
