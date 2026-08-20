package com.minhnb.finvera_be.analyst.service;

import com.minhnb.finvera_be.analyst.dto.EvidenceFactorDto;
import com.minhnb.finvera_be.analyst.dto.ToolResponseDtos.*;
import com.minhnb.finvera_be.market.domain.index.IndexOverview;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.service.MarketOverviewService;
import com.minhnb.finvera_be.portfolio.dto.PortfolioAnalyticsResponse;
import com.minhnb.finvera_be.portfolio.dto.PortfolioSummaryResponse;
import com.minhnb.finvera_be.portfolio.dto.PositionsResponse;
import com.minhnb.finvera_be.portfolio.service.PortfolioAnalyticsService;
import com.minhnb.finvera_be.portfolio.service.PortfolioService;
import com.minhnb.finvera_be.portfolio.service.PositionService;
import com.minhnb.finvera_be.research.dto.NewsArticlePageResponse;
import com.minhnb.finvera_be.research.service.NewsArticleService;
import com.minhnb.finvera_be.stock.dto.ScreenRequest;
import com.minhnb.finvera_be.stock.dto.ScreenResponse;
import com.minhnb.finvera_be.stock.service.FundamentalReportService;
import com.minhnb.finvera_be.stock.service.StockOverviewService;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService;
import com.minhnb.finvera_be.stock.service.ValuationService;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service delegates for the nine tools (DATA-001, DATA-002, research R-004).
 * Thin wrappers invoking existing services without modifying authoritative calculations.
 */
@Service
@Transactional(readOnly = true)
public class ToolDelegateService {

    private final MarketOverviewService marketOverviewService;
    private final StockOverviewService stockOverviewService;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final FundamentalReportService fundamentalReportService;
    private final ValuationService valuationService;
    private final StrategySignalService strategySignalService;
    private final PortfolioService portfolioService;
    private final PositionService positionService;
    private final PortfolioAnalyticsService portfolioAnalyticsService;
    private final NewsArticleService newsArticleService;
    private final ScreenerService screenerService;

    public ToolDelegateService(
            MarketOverviewService marketOverviewService,
            StockOverviewService stockOverviewService,
            TechnicalIndicatorService technicalIndicatorService,
            FundamentalReportService fundamentalReportService,
            ValuationService valuationService,
            StrategySignalService strategySignalService,
            PortfolioService portfolioService,
            PositionService positionService,
            PortfolioAnalyticsService portfolioAnalyticsService,
            NewsArticleService newsArticleService,
            ScreenerService screenerService) {
        this.marketOverviewService = marketOverviewService;
        this.stockOverviewService = stockOverviewService;
        this.technicalIndicatorService = technicalIndicatorService;
        this.fundamentalReportService = fundamentalReportService;
        this.valuationService = valuationService;
        this.strategySignalService = strategySignalService;
        this.portfolioService = portfolioService;
        this.positionService = positionService;
        this.portfolioAnalyticsService = portfolioAnalyticsService;
        this.newsArticleService = newsArticleService;
        this.screenerService = screenerService;
    }

    public MarketOverviewToolResponse getMarketOverview() {
        MarketOverviewService.MarketOverview latest = marketOverviewService.latest();
        String vnIndexValue = "0";
        String vnIndexChangePercent = "0";
        if (latest.indices() != null && latest.indices().indices() != null) {
            for (var fact : latest.indices().indices()) {
                if (fact.code() == IndexCode.VN_INDEX) {
                    vnIndexValue = fact.level() != null ? fact.level().toPlainString() : "0";
                    vnIndexChangePercent = fact.percentageChange() != null ? fact.percentageChange().toPlainString() : "0";
                    break;
                }
            }
        }

        int advancers = 0;
        int decliners = 0;
        int unchanged = 0;
        if (latest.breadth() != null && latest.breadth().result() != null) {
            advancers = latest.breadth().result().advancing();
            decliners = latest.breadth().result().declining();
            unchanged = latest.breadth().result().unchanged();
        }

        Map<String, Object> raw = new HashMap<>();
        raw.put("marketRegime", latest.regime() != null && latest.regime().assessment() != null
                ? latest.regime().assessment().label().name() : null);
        raw.put("breadth", latest.breadth());

        Instant asOf = latest.indices() != null ? latest.indices().observedAt() : latest.generatedAt();

        return new MarketOverviewToolResponse(
                vnIndexValue,
                vnIndexChangePercent,
                advancers,
                decliners,
                unchanged,
                asOf,
                raw);
    }

    public StockSummaryToolResponse getStockSummary(String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        var overviewOpt = stockOverviewService.findBySymbol(cleanSymbol);
        if (overviewOpt.isEmpty()) {
            return new StockSummaryToolResponse(cleanSymbol, cleanSymbol, "0", "0", 0L, Instant.now(), Collections.emptyMap());
        }
        var overview = overviewOpt.get();
        Map<String, Object> raw = new HashMap<>();
        raw.put("venue", overview.venue());
        raw.put("sector", overview.sector());

        String priceStr = overview.price() != null && overview.price().lastPrice() != null
                ? overview.price().lastPrice().toPlainString() : "0";
        String changeStr = overview.price() != null && overview.price().percentageChange() != null
                ? overview.price().percentageChange().toPlainString() : "0";
        Long vol = overview.price() != null ? overview.price().volume() : null;

        return new StockSummaryToolResponse(
                overview.symbol(),
                overview.companyNameVi() != null ? overview.companyNameVi() : overview.symbol(),
                priceStr,
                changeStr,
                vol,
                overview.asOf(),
                raw);
    }

    public TechnicalToolResponse getTechnical(String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        var technicalOpt = technicalIndicatorService.findBySymbol(cleanSymbol);
        var signalsOpt = strategySignalService.findBySymbol(cleanSymbol);

        Instant asOf = technicalOpt.map(TechnicalIndicatorService.StockTechnical::asOf)
                .orElseGet(Instant::now);

        TechnicalSignalDto signalDto = null;
        List<EvidenceFactorDto> riskFactors = Collections.emptyList();

        if (signalsOpt.isPresent() && signalsOpt.get().evaluations() != null) {
            var evaluations = signalsOpt.get().evaluations();
            var firstSignal = evaluations.stream()
                    .filter(e -> e.signal() != null)
                    .findFirst()
                    .map(StrategySignalService.StrategyEvaluationResult::signal)
                    .orElse(null);

            if (firstSignal != null) {
                List<EvidenceFactorDto> evidence = firstSignal.supportingEvidence() != null
                        ? firstSignal.supportingEvidence().entrySet().stream()
                                .map(entry -> new EvidenceFactorDto(entry.getKey(), entry.getValue()))
                                .toList()
                        : Collections.emptyList();

                signalDto = new TechnicalSignalDto(
                        firstSignal.direction() != null ? firstSignal.direction().name() : "NEUTRAL",
                        evidence);

                if (firstSignal.riskFactors() != null) {
                    riskFactors = firstSignal.riskFactors().stream()
                            .map(rf -> new EvidenceFactorDto(
                                    rf.factorCode() != null ? rf.factorCode().name() : "RISK",
                                    rf.reasonCode() != null ? rf.reasonCode() : ""))
                            .toList();
                }
            }
        }

        Map<String, Object> indicators = new HashMap<>();
        if (technicalOpt.isPresent() && technicalOpt.get().indicators() != null) {
            technicalOpt.get().indicators().forEach(ind -> {
                if (ind.indicatorCode() != null) {
                    indicators.put(ind.indicatorCode().name(), ind);
                }
            });
        }

        return new TechnicalToolResponse(
                cleanSymbol,
                indicators,
                signalDto,
                riskFactors,
                asOf);
    }

    public FundamentalsToolResponse getFundamentals(String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        var reportOpt = fundamentalReportService.findBySymbol(cleanSymbol);
        if (reportOpt.isEmpty()) {
            return new FundamentalsToolResponse(cleanSymbol, null, null, null, "N/A", Instant.now(), Collections.emptyMap());
        }
        var report = reportOpt.get();
        Map<String, Object> raw = new HashMap<>();

        String eps = null;
        String roe = null;
        String revGrowth = null;

        if (report.metrics() != null) {
            for (var m : report.metrics()) {
                raw.put(m.metricCode(), m.value());
                if ("EPS".equalsIgnoreCase(m.metricCode()) && m.value() != null) {
                    eps = m.value().toPlainString();
                } else if ("ROE".equalsIgnoreCase(m.metricCode()) && m.value() != null) {
                    roe = m.value().toPlainString();
                } else if ("REVENUE_GROWTH".equalsIgnoreCase(m.metricCode()) && m.value() != null) {
                    revGrowth = m.value().toPlainString();
                }
            }
        }

        String period = report.basisPeriodLabel() != null ? report.basisPeriodLabel() : "ANNUAL";

        return new FundamentalsToolResponse(
                report.symbol(),
                eps,
                roe,
                revGrowth,
                period,
                report.asOf(),
                raw);
    }

    public ValuationToolResponse getValuation(String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        var valuationOpt = valuationService.findBySymbol(cleanSymbol);
        if (valuationOpt.isEmpty()) {
            return new ValuationToolResponse(cleanSymbol, null, null, "UNCLASSIFIED", "NONE", Instant.now(), Collections.emptyMap());
        }
        var valuation = valuationOpt.get();
        Map<String, Object> raw = new HashMap<>();

        String pe = null;
        String pb = null;

        if (valuation.metrics() != null) {
            for (var m : valuation.metrics()) {
                raw.put(m.metricCode(), m.value());
                if ("PE_RATIO".equalsIgnoreCase(m.metricCode()) && m.value() != null) {
                    pe = m.value().toPlainString();
                } else if ("PB_RATIO".equalsIgnoreCase(m.metricCode()) && m.value() != null) {
                    pb = m.value().toPlainString();
                }
            }
        }

        String classification = valuation.classification() != null ? valuation.classification().name() : "FAIR_VALUE";
        String basis = valuation.usedSector() ? "SECTOR" : (valuation.usedOwnHistory() ? "HISTORY" : "NONE");

        return new ValuationToolResponse(
                valuation.symbol(),
                pe,
                pb,
                classification,
                basis,
                valuation.asOf(),
                raw);
    }

    public PortfolioPositionsToolResponse getPortfolioPositions(UUID ownerId) {
        List<PortfolioSummaryResponse> portfolios = portfolioService.listPortfolios();
        if (portfolios.isEmpty()) {
            return new PortfolioPositionsToolResponse(Collections.emptyList(), Instant.now());
        }

        UUID portfolioId = portfolios.getFirst().id();
        PositionsResponse positionsResponse = positionService.getPositions(portfolioId);
        List<PositionItemDto> items = positionsResponse.positions().stream()
                .map(p -> new PositionItemDto(
                        p.instrumentSymbol(),
                        p.quantity(),
                        p.currentPrice(),
                        p.unrealizedPL()))
                .toList();

        return new PortfolioPositionsToolResponse(items, positionsResponse.asOf());
    }

    public PortfolioAnalyticsToolResponse getPortfolioAnalytics(UUID ownerId) {
        List<PortfolioSummaryResponse> portfolios = portfolioService.listPortfolios();
        if (portfolios.isEmpty()) {
            return new PortfolioAnalyticsToolResponse("0", "0", Instant.now(), Collections.emptyMap());
        }

        UUID portfolioId = portfolios.getFirst().id();
        PortfolioAnalyticsResponse analytics = portfolioAnalyticsService.getPortfolioAnalytics(portfolioId, null, null);

        Map<String, Object> raw = new HashMap<>();
        raw.put("riskExposure", analytics.riskExposure());
        raw.put("stockConcentration", analytics.stockConcentration());

        return new PortfolioAnalyticsToolResponse(
                analytics.returnSinceInception() != null ? analytics.returnSinceInception() : "0",
                analytics.returnOverPeriod() != null ? analytics.returnOverPeriod() : "0",
                analytics.asOf(),
                raw);
    }

    public NewsBrowseToolResponse getNewsArticles(UUID ownerId, String symbol, int limit) {
        String cleanSymbol = symbol != null && !symbol.isBlank() ? normalizeSymbol(symbol) : null;
        NewsArticlePageResponse pageResult = newsArticleService.listNewsArticles(
                cleanSymbol,
                null,
                null,
                null,
                null,
                Math.clamp(limit, 1, 20),
                0);

        List<NewsArticleItemDto> items = pageResult.items().stream()
                .map(a -> new NewsArticleItemDto(
                        a.id(),
                        a.title(),
                        a.source(),
                        a.publishedAt(),
                        a.category() != null ? a.category().name() : null,
                        a.sentiment() != null ? a.sentiment().name() : null))
                .toList();

        return new NewsBrowseToolResponse(items, Instant.now());
    }

    public ScreenerExecutionToolResponse executeScreener(ScreenRequest request) {
        ScreenRequest effective = request == null
                ? new ScreenRequest(null, null, null, null, null, null, null, null)
                : request;
        var result = screenerService.execute(
                effective.toCriteria(),
                effective.effectiveSortField(),
                effective.effectiveSortDirection(),
                effective.effectiveLimit(),
                effective.effectiveOffset());

        ScreenResponse response = ScreenResponse.from(result, effective.effectiveLimit(), effective.effectiveOffset());
        List<Map<String, Object>> matches = response.matches().stream()
                .map(item -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("symbol", item.symbol());
                    map.put("companyName", item.companyName());
                    map.put("exchange", item.exchange());
                    map.put("sectorName", item.sectorName());
                    map.put("matchedValues", item.matchedValues());
                    return map;
                })
                .toList();

        return new ScreenerExecutionToolResponse(matches, response.totalMatchCount(), Instant.now());
    }

    private static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "";
        }
        return symbol.trim().toUpperCase();
    }
}
