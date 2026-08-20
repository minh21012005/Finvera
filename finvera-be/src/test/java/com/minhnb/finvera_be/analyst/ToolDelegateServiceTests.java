package com.minhnb.finvera_be.analyst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import com.minhnb.finvera_be.stock.service.screener.ScreenerService;
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
        BreadthService.Snapshot breadthSnapshot = new BreadthService.Snapshot(UUID.randomUUID(), LocalDate.of(2026, 8, 20), asOf, DataStatus.CURRENT, breadthResult, "v1", "hash");

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
}
