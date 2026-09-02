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
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.service.FundamentalReportService;
import com.minhnb.finvera_be.stock.service.StockOverviewService;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService;
import com.minhnb.finvera_be.stock.service.ValuationService;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
            return new StockSummaryToolResponse(cleanSymbol, cleanSymbol, null, null, null, Instant.now(),
                    Collections.emptyMap(), "UNAVAILABLE", List.of("UNKNOWN_SYMBOL"));
        }
        var overview = overviewOpt.get();
        Map<String, Object> raw = new HashMap<>();
        raw.put("venue", overview.venue());
        raw.put("sector", overview.sector());
        raw.put("tradingDate", overview.tradingDate() != null ? overview.tradingDate().toString() : null);

        var price = overview.price();
        // T049: a price that is not DEFINED is reported as null with its reason, never as "0".
        boolean priceDefined = price != null && price.priceApplicability() == MetricApplicability.DEFINED
                && price.lastPrice() != null;
        String priceStr = priceDefined ? price.lastPrice().toPlainString() : null;
        String changeStr = priceDefined && price.percentageChange() != null ? price.percentageChange().toPlainString() : null;
        Long vol = priceDefined ? price.volume() : null;
        if (priceDefined) {
            raw.put("referencePrice", price.referencePrice() != null ? price.referencePrice().toPlainString() : null);
            raw.put("marketCapVnd", price.marketCapVnd() != null ? price.marketCapVnd().toPlainString() : null);
        }

        return new StockSummaryToolResponse(
                overview.symbol(),
                overview.companyNameVi() != null ? overview.companyNameVi() : overview.symbol(),
                priceStr,
                changeStr,
                vol,
                overview.asOf(),
                raw,
                overview.dataStatus() != null ? overview.dataStatus().name() : "UNAVAILABLE",
                overview.reasonCodes() != null ? overview.reasonCodes() : List.of());
    }

    /**
     * Q-49: the underlying service materialises its result on read (idempotent revision
     * chain). Under the class-level read-only transaction Hibernate never flushed, so the
     * fundamentals tool silently dropped its writes and the valuation tool — whose
     * {@code saveAndFlush} forced a flush — failed with "cannot execute INSERT in a
     * read-only transaction" (surfaced to finvera-ai as HTTP 401 via /error). Writable, like
     * the public stock-detail endpoints that call the same services.
     */
    @Transactional
    public TechnicalToolResponse getTechnical(String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        var technicalOpt = technicalIndicatorService.findBySymbol(cleanSymbol);
        var signalsOpt = strategySignalService.findBySymbol(cleanSymbol);

        Instant asOf = technicalOpt.map(TechnicalIndicatorService.StockTechnical::asOf)
                .orElseGet(Instant::now);

        // T049: every triggered strategy is reported with its own levels and risk; the legacy
        // single `signal` slot carries the strongest one (highest strength, then lowest risk score).
        List<TechnicalSignalDto> signals = new java.util.ArrayList<>();
        if (signalsOpt.isPresent() && signalsOpt.get().evaluations() != null) {
            for (var evaluation : signalsOpt.get().evaluations()) {
                var sig = evaluation.signal();
                if (sig == null) {
                    continue;
                }
                List<EvidenceFactorDto> evidence = sig.supportingEvidence() != null
                        ? sig.supportingEvidence().entrySet().stream()
                                .map(entry -> new EvidenceFactorDto(entry.getKey(), entry.getValue()))
                                .toList()
                        : Collections.emptyList();
                List<EvidenceFactorDto> riskFactors = sig.riskFactors() != null
                        ? sig.riskFactors().stream()
                                .map(rf -> new EvidenceFactorDto(
                                        rf.factorCode() != null ? rf.factorCode().name() : "RISK",
                                        rf.applicability() == MetricApplicability.DEFINED
                                                ? "value=" + (rf.inputValue() != null ? rf.inputValue().toPlainString() : "n/a")
                                                        + " score=" + rf.factorScore() + "/100"
                                                : "UNAVAILABLE" + (rf.reasonCode() != null ? " (" + rf.reasonCode() + ")" : "")))
                                .toList()
                        : Collections.emptyList();
                var levels = sig.levels();
                signals.add(new TechnicalSignalDto(
                        sig.direction() != null ? sig.direction().name() : "NEUTRAL",
                        evidence,
                        sig.strategyCode() != null ? sig.strategyCode().name() : null,
                        levels != null && levels.entryLow() != null ? levels.entryLow().toPlainString() : null,
                        levels != null && levels.entryHigh() != null ? levels.entryHigh().toPlainString() : null,
                        levels != null && levels.stopLoss() != null ? levels.stopLoss().toPlainString() : null,
                        levels != null && levels.target1() != null ? levels.target1().toPlainString() : null,
                        levels != null && levels.target2() != null ? levels.target2().toPlainString() : null,
                        levels != null && levels.riskReward() != null ? levels.riskReward().toPlainString() : null,
                        sig.riskScore(),
                        sig.riskLevel() != null ? sig.riskLevel().name() : null,
                        sig.signalStrength() != null ? sig.signalStrength().name() : null,
                        riskFactors));
            }
        }
        TechnicalSignalDto strongest = signals.stream()
                .sorted(java.util.Comparator
                        .comparingInt((TechnicalSignalDto d) -> strengthRank(d.signalStrength())).reversed()
                        .thenComparingInt(d -> d.riskScore() != null ? d.riskScore() : Integer.MAX_VALUE))
                .findFirst().orElse(null);

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
                strongest,
                strongest != null ? strongest.riskFactors() : Collections.emptyList(),
                asOf,
                signals,
                technicalOpt.map(t -> t.dataStatus() != null ? t.dataStatus().name() : "UNAVAILABLE").orElse("UNAVAILABLE"));
    }

    private static int strengthRank(String strength) {
        if (strength == null) return 0;
        return switch (strength) {
            case "STRONG" -> 3;
            case "MODERATE" -> 2;
            case "WEAK" -> 1;
            default -> 0;
        };
    }

    /**
     * Q-49: the underlying service materialises its result on read (idempotent revision
     * chain). Under the class-level read-only transaction Hibernate never flushed, so the
     * fundamentals tool silently dropped its writes and the valuation tool — whose
     * {@code saveAndFlush} forced a flush — failed with "cannot execute INSERT in a
     * read-only transaction" (surfaced to finvera-ai as HTTP 401 via /error). Writable, like
     * the public stock-detail endpoints that call the same services.
     */
    @Transactional
    public FundamentalsToolResponse getFundamentals(String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        var reportOpt = fundamentalReportService.findBySymbol(cleanSymbol);
        if (reportOpt.isEmpty()) {
            return new FundamentalsToolResponse(cleanSymbol, null, null, null, "N/A", Instant.now(), Collections.emptyMap(),
                    null, null, List.of(), "UNAVAILABLE", List.of("NO_FUNDAMENTAL_REPORT"));
        }
        var report = reportOpt.get();
        Map<String, Object> raw = new HashMap<>();
        List<MetricFactDto> facts = new java.util.ArrayList<>();

        String eps = null;
        String roe = null;
        String revGrowth = null;
        String epsTtm = null;
        String epsGrowth = null;

        if (report.metrics() != null) {
            for (var m : report.metrics()) {
                boolean defined = m.applicability() == MetricApplicability.DEFINED && m.value() != null;
                String value = defined ? m.value().toPlainString() : null;
                if (defined) {
                    raw.put(m.metricCode(), value);
                }
                facts.add(new MetricFactDto(m.metricCode(), value,
                        m.applicability() != null ? m.applicability().name() : "MISSING",
                        m.reasonCode(), null, null, null));
                if (!defined) {
                    continue;
                }
                // T049: metric codes are the catalog's, not guessed aliases.
                switch (m.metricCode()) {
                    case "EPS" -> eps = value;
                    case "ROE" -> roe = value;
                    case "REVENUE_GROWTH_PERCENT" -> revGrowth = value;
                    case "EPS_TTM" -> epsTtm = value;
                    case "EPS_GROWTH_PERCENT" -> epsGrowth = value;
                    default -> { }
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
                raw,
                epsTtm,
                epsGrowth,
                facts,
                report.dataStatus() != null ? report.dataStatus().name() : "UNAVAILABLE",
                report.reasonCodes() != null ? report.reasonCodes() : List.of());
    }

    /**
     * Q-49: the underlying service materialises its result on read (idempotent revision
     * chain). Under the class-level read-only transaction Hibernate never flushed, so the
     * fundamentals tool silently dropped its writes and the valuation tool — whose
     * {@code saveAndFlush} forced a flush — failed with "cannot execute INSERT in a
     * read-only transaction" (surfaced to finvera-ai as HTTP 401 via /error). Writable, like
     * the public stock-detail endpoints that call the same services.
     */
    @Transactional
    public ValuationToolResponse getValuation(String symbol) {
        String cleanSymbol = normalizeSymbol(symbol);
        var valuationOpt = valuationService.findBySymbol(cleanSymbol);
        if (valuationOpt.isEmpty()) {
            return new ValuationToolResponse(cleanSymbol, null, null, null, "NONE", Instant.now(), Collections.emptyMap(),
                    false, null, null, null, null, null, null, List.of(), List.of("NO_VALUATION"), "UNAVAILABLE", null, Map.of());
        }
        var valuation = valuationOpt.get();
        Map<String, Object> raw = new HashMap<>();
        List<MetricFactDto> facts = new java.util.ArrayList<>();

        String pe = null;
        String pb = null;

        if (valuation.metrics() != null) {
            for (var m : valuation.metrics()) {
                boolean defined = m.applicability() == MetricApplicability.DEFINED && m.value() != null;
                String value = defined ? m.value().toPlainString() : null;
                if (defined) {
                    raw.put(m.metricCode(), value);
                }
                facts.add(new MetricFactDto(m.metricCode(), value,
                        m.applicability() != null ? m.applicability().name() : "MISSING", m.reasonCode(),
                        m.ownHistoryPercentile() != null ? m.ownHistoryPercentile().toPlainString() : null,
                        m.sectorPercentile() != null ? m.sectorPercentile().toPlainString() : null,
                        m.effectiveWeight() != null ? m.effectiveWeight().toPlainString() : null));
                if (defined && "PE".equals(m.metricCode())) {
                    pe = value;
                } else if (defined && "PB".equals(m.metricCode())) {
                    pb = value;
                }
            }
        }

        // T049: a withheld assessment has NO classification; the reason codes say why.
        String classification = valuation.published() && valuation.classification() != null
                ? valuation.classification().name() : null;
        String basis = valuation.usedOwnHistory() && valuation.usedSector() ? "OWN_HISTORY+SECTOR"
                : valuation.usedSector() ? "SECTOR"
                : valuation.usedOwnHistory() ? "OWN_HISTORY" : "NONE";

        return new ValuationToolResponse(
                valuation.symbol(),
                pe,
                pb,
                classification,
                basis,
                valuation.asOf(),
                raw,
                valuation.published(),
                valuation.ruleVersion(),
                valuation.score() != null ? valuation.score().toPlainString() : null,
                valuation.displayedScore(),
                valuation.confidence(),
                valuation.historyPointCount(),
                valuation.sectorConstituentCount(),
                facts,
                valuation.reasonCodes() != null ? valuation.reasonCodes() : List.of(),
                valuation.dataStatus() != null ? valuation.dataStatus().name() : "UNAVAILABLE",
                valuation.priceTradingDate() != null ? valuation.priceTradingDate().toString() : null,
                valuation.inputBasis() != null ? valuation.inputBasis() : Map.of());
    }

    public PortfolioPositionsToolResponse getPortfolioPositions(UUID ownerId) {
        List<PortfolioSummaryResponse> portfolios = portfolioService.listPortfolios();
        if (portfolios.isEmpty()) {
            return new PortfolioPositionsToolResponse(Collections.emptyList(), Instant.now());
        }

        UUID portfolioId = portfolios.getFirst().id();
        PositionsResponse positionsResponse = positionService.getPositions(portfolioId);
        List<PositionItemDto> items = positionsResponse.positions().stream()
                .map(p -> {
                    String marketVal = null;
                    String unplPct = null;
                    try {
                        BigDecimal qty = p.quantity() != null ? new BigDecimal(p.quantity()) : BigDecimal.ZERO;
                        BigDecimal price = p.currentPrice() != null ? new BigDecimal(p.currentPrice()) : BigDecimal.ZERO;
                        BigDecimal cost = p.averageCostBasis() != null ? new BigDecimal(p.averageCostBasis()) : BigDecimal.ZERO;
                        BigDecimal mkt = qty.multiply(price);
                        marketVal = mkt.toPlainString();
                        if (cost.signum() > 0 && price.signum() > 0) {
                            unplPct = price.subtract(cost).divide(cost, 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100)).toPlainString();
                        }
                    } catch (Exception ignored) {
                    }
                    return new PositionItemDto(
                            p.instrumentSymbol(),
                            p.quantity(),
                            p.averageCostBasis(),
                            p.currentPrice(),
                            marketVal,
                            p.unrealizedPL(),
                            unplPct,
                            p.allocation());
                })
                .toList();

        return new PortfolioPositionsToolResponse(items, positionsResponse.asOf());
    }

    public PortfolioAnalyticsToolResponse getPortfolioAnalytics(UUID ownerId) {
        List<PortfolioSummaryResponse> portfolios = portfolioService.listPortfolios();
        if (portfolios.isEmpty()) {
            return new PortfolioAnalyticsToolResponse("0", "0", "0", "0", Instant.now(), Collections.emptyMap());
        }

        PortfolioSummaryResponse summary = portfolios.getFirst();
        UUID portfolioId = summary.id();
        PortfolioAnalyticsResponse analytics = portfolioAnalyticsService.getPortfolioAnalytics(portfolioId, null, null);

        Map<String, Object> raw = new HashMap<>();
        raw.put("riskExposure", analytics.riskExposure());
        raw.put("stockConcentration", analytics.stockConcentration());
        raw.put("returnSinceInception", analytics.returnSinceInception());
        raw.put("returnOverPeriod", analytics.returnOverPeriod());
        raw.put("maxDrawdown", analytics.maxDrawdown());
        raw.put("cashBalance", summary.cashBalance());

        String unrealizedPnlPercent = calculateUnrealizedPnlPercent(summary);

        return new PortfolioAnalyticsToolResponse(
                summary.totalValue() != null ? summary.totalValue() : "0",
                summary.cashBalance() != null ? summary.cashBalance() : "0",
                summary.totalUnrealizedPL() != null ? summary.totalUnrealizedPL() : "0",
                unrealizedPnlPercent,
                analytics.asOf(),
                raw);
    }

    /**
     * Contracted percentage-points value for unrealized P/L. Portfolio return since
     * inception is a different metric and must never be relabelled as unrealized P/L.
     */
    private static String calculateUnrealizedPnlPercent(PortfolioSummaryResponse summary) {
        try {
            BigDecimal totalValue = new BigDecimal(summary.totalValue() != null ? summary.totalValue() : "0");
            BigDecimal cashBalance = new BigDecimal(summary.cashBalance() != null ? summary.cashBalance() : "0");
            BigDecimal unrealizedPnl = new BigDecimal(
                    summary.totalUnrealizedPL() != null ? summary.totalUnrealizedPL() : "0");
            BigDecimal openCostBasis = totalValue.subtract(cashBalance).subtract(unrealizedPnl);
            if (openCostBasis.signum() <= 0) {
                return "0";
            }
            return unrealizedPnl.multiply(BigDecimal.valueOf(100))
                    .divide(openCostBasis, 6, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString();
        } catch (NumberFormatException ignored) {
            return "0";
        }
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
