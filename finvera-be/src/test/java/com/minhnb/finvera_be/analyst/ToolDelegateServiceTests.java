package com.minhnb.finvera_be.analyst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.analyst.dto.ToolResponseDtos.*;
import com.minhnb.finvera_be.analyst.service.ToolDelegateService;
import com.minhnb.finvera_be.market.domain.breadth.BreadthCalculator;
import com.minhnb.finvera_be.market.domain.index.IndexOverview;
import com.minhnb.finvera_be.market.domain.index.IndexOverview.IndexFact;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Direction;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.RegimeLabel;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Venue;
import com.minhnb.finvera_be.market.domain.regime.RegimeAssessment;
import com.minhnb.finvera_be.market.service.BreadthService;
import com.minhnb.finvera_be.market.service.MarketOverviewService;
import com.minhnb.finvera_be.market.service.MarketOverviewService.MarketOverview;
import com.minhnb.finvera_be.market.service.RegimeAssessmentService;
import com.minhnb.finvera_be.portfolio.dto.PositionsResponse;
import com.minhnb.finvera_be.portfolio.dto.PortfolioAnalyticsResponse;
import com.minhnb.finvera_be.portfolio.dto.PortfolioSummaryResponse;
import com.minhnb.finvera_be.portfolio.service.PortfolioAnalyticsService;
import com.minhnb.finvera_be.portfolio.service.PortfolioService;
import com.minhnb.finvera_be.portfolio.service.PositionService;
import com.minhnb.finvera_be.research.dto.NewsArticlePageResponse;
import com.minhnb.finvera_be.research.service.NewsArticleService;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.overview.StockOverviewCalculator.StockOverviewResult;
import com.minhnb.finvera_be.stock.service.FundamentalReportService;
import com.minhnb.finvera_be.stock.service.StockOverviewService;
import com.minhnb.finvera_be.stock.service.StockOverviewService.StockOverview;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService;
import com.minhnb.finvera_be.stock.service.ValuationService;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService;
import com.minhnb.finvera_be.stock.service.strategy.StrategyScanService;
import com.minhnb.finvera_be.stock.service.strategy.StrategyScanService.ScanResult;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolDelegateServiceTests {

    private MarketOverviewService marketOverviewService;
    private StockOverviewService stockOverviewService;
    private TechnicalIndicatorService technicalIndicatorService;
    private FundamentalReportService fundamentalReportService;
    private ValuationService valuationService;
    private StrategySignalService strategySignalService;
    private StrategyScanService strategyScanService;
    private PortfolioService portfolioService;
    private PositionService positionService;
    private PortfolioAnalyticsService portfolioAnalyticsService;
    private NewsArticleService newsArticleService;
    private ScreenerService screenerService;

    private ToolDelegateService toolDelegateService;

    @BeforeEach
    void setUp() {
        marketOverviewService = mock(MarketOverviewService.class);
        stockOverviewService = mock(StockOverviewService.class);
        technicalIndicatorService = mock(TechnicalIndicatorService.class);
        fundamentalReportService = mock(FundamentalReportService.class);
        valuationService = mock(ValuationService.class);
        strategySignalService = mock(StrategySignalService.class);
        strategyScanService = mock(StrategyScanService.class);
        portfolioService = mock(PortfolioService.class);
        positionService = mock(PositionService.class);
        portfolioAnalyticsService = mock(PortfolioAnalyticsService.class);
        newsArticleService = mock(NewsArticleService.class);
        screenerService = mock(ScreenerService.class);

        toolDelegateService = new ToolDelegateService(
                marketOverviewService,
                stockOverviewService,
                technicalIndicatorService,
                fundamentalReportService,
                valuationService,
                strategySignalService,
                strategyScanService,
                portfolioService,
                positionService,
                portfolioAnalyticsService,
                newsArticleService,
                screenerService);
    }

    @Test
    void getMarketOverview_delegatesToMarketOverviewService() {
        Instant asOf = Instant.parse("2026-08-20T10:00:00Z");
        IndexFact vnIndex = new IndexFact(
                IndexCode.VN_INDEX,
                Venue.HOSE,
                new BigDecimal("1280.50"),
                new BigDecimal("15.20"),
                new BigDecimal("1.20"),
                500000000L,
                new BigDecimal("15000000000"),
                Direction.UP,
                DataStatus.CURRENT,
                Collections.emptyList());

        IndexOverview indices = new IndexOverview(
                LocalDate.of(2026, 8, 20),
                asOf,
                SessionState.OPEN,
                DataStatus.CURRENT,
                1L,
                "TEST",
                List.of(vnIndex));

        BreadthCalculator.Result breadthResult = new BreadthCalculator.Result(280, 120, 50, 0, 450, Collections.emptyList());
        BreadthService.Snapshot breadthSnapshot = new BreadthService.Snapshot(UUID.randomUUID(), LocalDate.of(2026, 8, 20), asOf, DataStatus.CURRENT, "EOD", breadthResult, "v1", "hash");

        RegimeAssessment regimeAssessment = new RegimeAssessment(
                DataStatus.CURRENT,
                RegimeLabel.BULL,
                85,
                90,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                false,
                List.of(),
                List.of());
        RegimeAssessmentService.Snapshot regimeSnapshot = new RegimeAssessmentService.Snapshot(LocalDate.of(2026, 8, 20), asOf, "market-regime-v1", regimeAssessment);

        MarketOverview overview = new MarketOverview(
                asOf,
                indices,
                breadthSnapshot,
                regimeSnapshot,
                DataStatus.CURRENT,
                "ETAG");

        when(marketOverviewService.latest()).thenReturn(overview);

        MarketOverviewToolResponse response = toolDelegateService.getMarketOverview();

        assertThat(response.vnIndexValue()).isEqualTo("1280.50");
        assertThat(response.vnIndexChangePercent()).isEqualTo("1.20");
        assertThat(response.advancers()).isEqualTo(280);
        assertThat(response.decliners()).isEqualTo(120);
        assertThat(response.unchanged()).isEqualTo(50);
        assertThat(response.asOf()).isEqualTo(asOf);
    }

    @Test
    void getPortfolioAnalytics_usesUnrealizedPnlPercentNotReturnSinceInception() {
        UUID portfolioId = UUID.randomUUID();
        Instant asOf = Instant.parse("2026-09-02T03:00:00Z");
        var summary = new PortfolioSummaryResponse(
                portfolioId, "Danh mục", asOf, "130", "0", "30", "0",
                "CURRENT", List.of(), asOf);
        var analytics = new PortfolioAnalyticsResponse(
                null, null, false, "99", "88", null, null,
                List.of(), List.of(), List.of(), null, null, asOf);
        when(portfolioService.listPortfolios()).thenReturn(List.of(summary));
        when(portfolioAnalyticsService.getPortfolioAnalytics(portfolioId, null, null)).thenReturn(analytics);

        PortfolioAnalyticsToolResponse result = toolDelegateService.getPortfolioAnalytics(UUID.randomUUID());

        assertThat(result.totalUnrealizedPL()).isEqualTo("30");
        assertThat(result.totalUnrealizedPnlPercent()).isEqualTo("30");
        assertThat(result.totalUnrealizedPnlPercent()).isNotEqualTo(analytics.returnSinceInception());
    }

    @Test
    void getStockSummary_normalizesLowercaseSymbol() {
        Instant asOf = Instant.parse("2026-08-20T10:00:00Z");
        StockOverviewResult priceResult = new StockOverviewResult(
                MetricApplicability.DEFINED,
                new BigDecimal("28500"),
                new BigDecimal("28000"),
                new BigDecimal("500"),
                new BigDecimal("1.79"),
                Direction.UP,
                12000000L,
                new BigDecimal("340000000000"),
                new BigDecimal("165000000000000"),
                null);

        StockOverview overview = new StockOverview(
                "HPG",
                "HOSE",
                "Tập đoàn Hòa Phát",
                "Hoa Phat Group",
                "LISTED",
                "Vật liệu",
                "ICB",
                5800000000L,
                priceResult,
                SessionState.OPEN,
                LocalDate.of(2026, 8, 20),
                asOf,
                DataStatus.CURRENT,
                Collections.emptyList(),
                "HPG_COH");

        when(stockOverviewService.findBySymbol("HPG")).thenReturn(Optional.of(overview));

        StockSummaryToolResponse response = toolDelegateService.getStockSummary("  hpg  ");

        assertThat(response.symbol()).isEqualTo("HPG");
        assertThat(response.companyName()).isEqualTo("Tập đoàn Hòa Phát");
        assertThat(response.price()).isEqualTo("28500");
        assertThat(response.changePercent()).isEqualTo("1.79");
        assertThat(response.volume()).isEqualTo(12000000L);
    }

    @Test
    void getPortfolioPositions_whenEmpty_returnsEmptyList() {
        when(portfolioService.listPortfolios()).thenReturn(Collections.emptyList());

        PortfolioPositionsToolResponse response = toolDelegateService.getPortfolioPositions(UUID.randomUUID());

        assertThat(response.positions()).isEmpty();
        assertThat(response.asOf()).isNotNull();
    }

    @Test
    void getNewsArticles_normalizesSymbol() {
        when(newsArticleService.listNewsArticles(eq("VNM"), any(), any(), any(), any(), eq(5), eq(0)))
                .thenReturn(new NewsArticlePageResponse(Collections.emptyList(), 0L, 5, 0));

        NewsBrowseToolResponse response = toolDelegateService.getNewsArticles(UUID.randomUUID(), "vnm", 5);

        assertThat(response.articles()).isEmpty();
        assertThat(response.asOf()).isNotNull();
    }

    @Test
    void executeScreener_delegatesDirectlyToScreenerService() {
        var mockResult = new com.minhnb.finvera_be.stock.service.screener.ScreenerService.ScreenExecutionResult(
                List.of(new com.minhnb.finvera_be.stock.service.screener.ScreenerService.ScreenMatch(
                        "HPG", "Hòa Phát", "HOSE", "Thép", Collections.emptyMap(), DataStatus.CURRENT, LocalDate.of(2026, 8, 20))),
                1,
                Collections.emptyList(),
                "HPG_COH",
                Instant.parse("2026-08-20T10:00:00Z"));

        when(screenerService.execute(any(), any(), any(), eq(50), eq(0))).thenReturn(mockResult);

        var request = new com.minhnb.finvera_be.stock.dto.ScreenRequest(
                null, null, null, null, null, null, 50, 0);

        var response = toolDelegateService.executeScreener(request);

        assertThat(response.totalMatches()).isEqualTo(1);
        assertThat(response.matches()).hasSize(1);
        assertThat(response.matches().getFirst().get("symbol")).isEqualTo("HPG");
    }

    // ── T049: tool payloads carry the engine's real result, never a defaulted one ──

    @Test
    void getValuation_withheldAssessment_hasNoClassificationAndCarriesReasons() {
        var withheld = new com.minhnb.finvera_be.stock.service.ValuationService.StockValuation(
                "ABC", "valuation-v2", false, null, null, null, null, false, false, null, null, null, null, 120,
                List.of(new com.minhnb.finvera_be.stock.service.ValuationService.ValuationMetric(
                        "PE", new BigDecimal("5.200000"), MetricApplicability.DEFINED, null, null, null, null, null, null)),
                DataStatus.CURRENT, List.of("NO_COMPARISON_BASIS", "HISTORY_BASIS_INSUFFICIENT"),
                LocalDate.of(2026, 8, 28), Instant.parse("2026-08-31T00:00:00Z"), "coh");
        when(valuationService.findBySymbol("ABC")).thenReturn(Optional.of(withheld));

        var response = toolDelegateService.getValuation("abc");

        assertThat(response.published()).isFalse();
        assertThat(response.classification()).isNull();                 // never "FAIR_VALUE"
        assertThat(response.reasonCodes()).contains("NO_COMPARISON_BASIS");
        assertThat(response.peRatio()).isEqualTo("5.200000");            // catalog code PE, not PE_RATIO
        assertThat(response.comparisonBasis()).isEqualTo("NONE");
    }

    @Test
    void getValuation_published_carriesScoreConfidencePercentilesAndWeights() {
        var published = new com.minhnb.finvera_be.stock.service.ValuationService.StockValuation(
                "MBB", "valuation-v2", true, com.minhnb.finvera_be.stock.domain.model.StockTypes.ValuationLabel.OVER_VALUED,
                new BigDecimal("69.12"), 69, 87, true, true, "Ngân hàng", "KBS", "1", 24, 750,
                List.of(new com.minhnb.finvera_be.stock.service.ValuationService.ValuationMetric(
                        "PB", new BigDecimal("1.270000"), MetricApplicability.DEFINED,
                        new BigDecimal("99.05"), new BigDecimal("66.67"), new BigDecimal("0.428571428571"), null,
                        "LATEST_REPORT", new BigDecimal("1.270000")),
                    new com.minhnb.finvera_be.stock.service.ValuationService.ValuationMetric(
                        "PEG", null, MetricApplicability.NOT_APPLICABLE, null, null, null, "NEGATIVE_OR_ZERO_GROWTH", null, null)),
                DataStatus.CURRENT, List.of("HISTORY_SHARES_OUTSTANDING_HELD_CURRENT"),
                LocalDate.of(2026, 8, 31), Instant.parse("2026-08-31T00:00:00Z"), "coh",
                LocalDate.of(2026, 8, 28), java.util.Map.of("EPS_GROWTH_PERCENT", "ANNUAL_BASIS", "DIVIDEND_YIELD", "ANNUAL_BASIS"));
        when(valuationService.findBySymbol("MBB")).thenReturn(Optional.of(published));

        var response = toolDelegateService.getValuation("MBB");

        assertThat(response.classification()).isEqualTo("OVER_VALUED");
        assertThat(response.displayedScore()).isEqualTo(69);
        assertThat(response.confidence()).isEqualTo(87);
        assertThat(response.comparisonBasis()).isEqualTo("OWN_HISTORY+SECTOR");
        assertThat(response.pbRatio()).isEqualTo("1.270000");
        var pb = response.metrics().stream().filter(m -> m.metricCode().equals("PB")).findFirst().orElseThrow();
        assertThat(pb.ownHistoryPercentile()).isEqualTo("99.05");
        assertThat(pb.effectiveWeight()).isEqualTo("0.428571428571");
        var peg = response.metrics().stream().filter(m -> m.metricCode().equals("PEG")).findFirst().orElseThrow();
        assertThat(peg.applicability()).isEqualTo("NOT_APPLICABLE");
        assertThat(peg.qualityReason()).isEqualTo("NEGATIVE_OR_ZERO_GROWTH");
        assertThat(response.raw()).doesNotContainKey("PEG");             // missing/N-A never masquerade as values
        assertThat(response.priceTradingDate()).isEqualTo("2026-08-28");  // the close that priced it, not the session date
        assertThat(response.inputBasis()).containsEntry("EPS_GROWTH_PERCENT", "ANNUAL_BASIS");
    }

    @Test
    void getFundamentals_usesCatalogCodesAndKeepsApplicabilityAndBasisReason() {
        var fundamentals = new com.minhnb.finvera_be.stock.service.FundamentalReportService.StockFundamentals(
                "VNM", "QUARTER", 2026, 2, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30), "CONSOLIDATED",
                "UNKNOWN", "VND", false, "2026-Q2",
                List.of(new com.minhnb.finvera_be.stock.service.FundamentalReportService.FundamentalMetric(
                                "EPS_TTM", new BigDecimal("4350.000000"), "VND", 2, MetricApplicability.DEFINED, null),
                        new com.minhnb.finvera_be.stock.service.FundamentalReportService.FundamentalMetric(
                                "EPS_GROWTH_PERCENT", new BigDecimal("9.464474"), "PERCENT", 2, MetricApplicability.DEFINED, "ANNUAL_BASIS"),
                        new com.minhnb.finvera_be.stock.service.FundamentalReportService.FundamentalMetric(
                                "REVENUE_GROWTH_PERCENT", new BigDecimal("3.333333"), "PERCENT", 2, MetricApplicability.DEFINED, "ANNUAL_BASIS"),
                        new com.minhnb.finvera_be.stock.service.FundamentalReportService.FundamentalMetric(
                                "FREE_CASH_FLOW", null, "VND", 0, MetricApplicability.MISSING, "NOT_REPORTED")),
                DataStatus.CURRENT, List.of(), LocalDate.of(2026, 8, 28), Instant.parse("2026-08-31T00:00:00Z"), "coh", null);
        when(fundamentalReportService.findBySymbol("VNM")).thenReturn(Optional.of(fundamentals));

        var response = toolDelegateService.getFundamentals("VNM");

        assertThat(response.revenueGrowthPercent()).isEqualTo("3.333333");   // was always null (REVENUE_GROWTH)
        assertThat(response.epsTtm()).isEqualTo("4350.000000");
        assertThat(response.epsGrowthPercent()).isEqualTo("9.464474");
        var growth = response.metrics().stream().filter(m -> m.metricCode().equals("EPS_GROWTH_PERCENT")).findFirst().orElseThrow();
        assertThat(growth.qualityReason()).isEqualTo("ANNUAL_BASIS");         // basis disclosure survives
        var fcf = response.metrics().stream().filter(m -> m.metricCode().equals("FREE_CASH_FLOW")).findFirst().orElseThrow();
        assertThat(fcf.applicability()).isEqualTo("MISSING");
        assertThat(response.raw()).doesNotContainKey("FREE_CASH_FLOW");
    }

    @Test
    void getStockSummary_unavailablePrice_isNullNotZero() {
        StockOverviewResult noPrice = new StockOverviewResult(MetricApplicability.MISSING, null, null, null, null,
                null, null, null, null, null, null, null, "PRICE_UNAVAILABLE");
        StockOverview overview = new StockOverview("XYZ", "UPCOM", "XYZ JSC", null, "LISTED", null, null, null,
                noPrice, SessionState.CLOSED, LocalDate.of(2026, 8, 28), Instant.parse("2026-08-31T00:00:00Z"),
                DataStatus.UNAVAILABLE, List.of("PRICE_UNAVAILABLE"), "coh");
        when(stockOverviewService.findBySymbol("XYZ")).thenReturn(Optional.of(overview));

        var response = toolDelegateService.getStockSummary("XYZ");

        assertThat(response.price()).isNull();
        assertThat(response.changePercent()).isNull();
        assertThat(response.dataStatus()).isEqualTo("UNAVAILABLE");
        assertThat(response.reasonCodes()).contains("PRICE_UNAVAILABLE");
    }

    @org.junit.jupiter.api.Test
    void materialisingToolsRunInAWritableTransaction() throws Exception {
        // Q-49: fundamentals / valuation / technical persist their revision chain on read.
        for (String method : new String[] {"getTechnical", "getFundamentals", "getValuation"}) {
            var tx = ToolDelegateService.class.getMethod(method, String.class)
                    .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
            assertThat(tx).as(method + " must override the class-level read-only transaction").isNotNull();
            assertThat(tx.readOnly()).as(method + " must be writable").isFalse();
        }
        var classTx = ToolDelegateService.class.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertThat(classTx.readOnly()).isTrue(); // every other tool stays read-only
    }

    @Test
    void scanStrategy_delegatesToService() {
        var mockScanResult = new ScanResult(StrategyCode.MOMENTUM, List.of(), 0, 5, 0, 0, Instant.now());
        when(strategyScanService.scan(StrategyCode.MOMENTUM, 5, 0)).thenReturn(mockScanResult);

        var response = toolDelegateService.scanStrategy(StrategyCode.MOMENTUM, 5);

        assertThat(response).isNotNull();
        assertThat(response.strategyCode()).isEqualTo("MOMENTUM");
        assertThat(response.totalMatchCount()).isEqualTo(0);
        verify(strategyScanService).scan(StrategyCode.MOMENTUM, 5, 0);
    }
}
