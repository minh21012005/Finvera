package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.domain.fundamentals.FundamentalSummaryCalculator;
import com.minhnb.finvera_be.stock.domain.fundamentals.FundamentalSummaryCalculator.ReportMetric;
import com.minhnb.finvera_be.stock.domain.fundamentals.FundamentalSummaryCalculator.ReportPeriod;
import com.minhnb.finvera_be.stock.domain.fundamentals.FundamentalSummaryCalculator.SummaryMetric;
import com.minhnb.finvera_be.stock.domain.fundamentals.FundamentalSummaryCalculator.SummaryResult;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.entity.FundamentalMetricCatalogEntity;
import com.minhnb.finvera_be.stock.entity.FundamentalReportEntity;
import com.minhnb.finvera_be.stock.entity.FundamentalReportMetricEntity;
import com.minhnb.finvera_be.stock.entity.FundamentalSummaryEntity;
import com.minhnb.finvera_be.stock.entity.FundamentalSummaryInputEntity;
import com.minhnb.finvera_be.stock.entity.FundamentalSummaryMetricEntity;
import com.minhnb.finvera_be.stock.repository.FundamentalMetricCatalogRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalReportMetricRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalReportRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalSummaryInputRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalSummaryMetricRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalSummaryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-007, FR-014, DATA-006, DATA-009; NFR-003.
 * Assembles the StockFundamentals read model and calculates/caches fundamental summaries.
 */
@Service
public class FundamentalReportService {

    private final MarketReferenceDataService referenceData;
    private final FundamentalReportRepository reports;
    private final FundamentalReportMetricRepository reportMetrics;
    private final FundamentalSummaryRepository summaries;
    private final FundamentalSummaryMetricRepository summaryMetrics;
    private final FundamentalSummaryInputRepository summaryInputs;
    private final FundamentalMetricCatalogRepository catalog;
    private final FundamentalSummaryCalculator calculator = new FundamentalSummaryCalculator();
    private final Clock clock;

    public FundamentalReportService(
            MarketReferenceDataService referenceData,
            FundamentalReportRepository reports,
            FundamentalReportMetricRepository reportMetrics,
            FundamentalSummaryRepository summaries,
            FundamentalSummaryMetricRepository summaryMetrics,
            FundamentalSummaryInputRepository summaryInputs,
            FundamentalMetricCatalogRepository catalog,
            Clock clock) {
        this.referenceData = referenceData;
        this.reports = reports;
        this.reportMetrics = reportMetrics;
        this.summaries = summaries;
        this.summaryMetrics = summaryMetrics;
        this.summaryInputs = summaryInputs;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Transactional
    public Optional<StockFundamentals> findBySymbol(String symbol) {
        Optional<InstrumentReference> instrumentOpt = referenceData.findActiveInstrumentBySymbol(symbol);
        if (instrumentOpt.isEmpty()) {
            return Optional.empty();
        }

        UUID instrumentId = instrumentOpt.get().instrumentId();
        List<FundamentalReportEntity> currentReports = reports.findAllByInstrumentIdAndCurrentTrueOrderByPeriodEndDesc(instrumentId);

        Instant asOf = clock.instant();
        LocalDate asOfDate = LocalDate.now(clock);

        if (currentReports.isEmpty()) {
            return Optional.of(new StockFundamentals(
                    symbol, null, null, null, null, null, null, null, "VND",
                    false, null, List.of(), DataStatus.UNAVAILABLE,
                    List.of("FUNDAMENTALS_UNAVAILABLE"), asOfDate, asOf,
                    CoherenceKeys.of(List.of("FUNDAMENTALS", symbol, "UNAVAILABLE"))
            ));
        }

        FundamentalReportEntity newest = currentReports.get(0);
        List<ReportPeriod> domainPeriods = new ArrayList<>();
        List<String> coherenceParts = new ArrayList<>();
        coherenceParts.add("FUNDAMENTALS");
        coherenceParts.add(symbol);

        // Fetch catalog metadata for units and precisions
        Map<String, FundamentalMetricCatalogEntity> catalogMap = new HashMap<>();
        for (FundamentalMetricCatalogEntity c : catalog.findByCatalogVersion("fundamental-metric-catalog-v1")) {
            catalogMap.put(c.getMetricCode(), c);
        }

        for (FundamentalReportEntity r : currentReports) {
            coherenceParts.add(r.getId().toString());
            coherenceParts.add(String.valueOf(r.getRevision()));

            List<FundamentalReportMetricEntity> rMetrics = reportMetrics.findByReportId(r.getId());
            List<ReportMetric> dMetrics = new ArrayList<>();
            for (FundamentalReportMetricEntity m : rMetrics) {
                dMetrics.add(new ReportMetric(
                        m.getMetricCode(),
                        m.getValue(),
                        MetricApplicability.valueOf(m.getApplicability()),
                        m.getQualityReason()
                ));
            }
            domainPeriods.add(new ReportPeriod(
                    r.getId(),
                    r.getPeriodType(),
                    r.getFiscalYear(),
                    r.getFiscalQuarter() == null ? null : (int) r.getFiscalQuarter(),
                    r.getPeriodStart(),
                    r.getPeriodEnd(),
                    r.getReportKind(),
                    dMetrics
            ));
        }

        SummaryResult summaryResult = calculator.calculate(domainPeriods, asOfDate);

        // Build presentation metrics: all metrics from newest report + summary metrics
        Map<String, FundamentalMetric> combined = new LinkedHashMap<>();
        List<FundamentalReportMetricEntity> newestReportMetrics = reportMetrics.findByReportId(newest.getId());
        for (FundamentalReportMetricEntity m : newestReportMetrics) {
            FundamentalMetricCatalogEntity cat = catalogMap.get(m.getMetricCode());
            String unit = cat != null ? cat.getUnitType() : "VND";
            int precision = cat != null ? cat.getScale() : 2;
            combined.put(m.getMetricCode(), new FundamentalMetric(
                    m.getMetricCode(),
                    m.getValue(),
                    unit,
                    precision,
                    MetricApplicability.valueOf(m.getApplicability()),
                    m.getQualityReason()
            ));
        }

        for (SummaryMetric sm : summaryResult.metrics()) {
            FundamentalMetricCatalogEntity cat = catalogMap.get(sm.metricCode());
            String unit = cat != null ? cat.getUnitType() : (sm.metricCode().contains("PERCENT") ? "PERCENT" : "VND");
            int precision = cat != null ? cat.getScale() : 2;
            combined.put(sm.metricCode(), new FundamentalMetric(
                    sm.metricCode(),
                    sm.value(),
                    unit,
                    precision,
                    sm.applicability(),
                    sm.qualityReason()
            ));
        }

        String coherenceKey = CoherenceKeys.of(coherenceParts);
        boolean restated = newest.getSupersedesId() != null;

        return Optional.of(new StockFundamentals(
                symbol,
                newest.getPeriodType(),
                (int) newest.getFiscalYear(),
                newest.getFiscalQuarter() == null ? null : (int) newest.getFiscalQuarter(),
                newest.getPeriodStart(),
                newest.getPeriodEnd(),
                newest.getReportKind(),
                newest.getAuditStatus(),
                newest.getCurrency(),
                restated,
                summaryResult.basisPeriodLabel(),
                new ArrayList<>(combined.values()),
                summaryResult.dataStatus(),
                summaryResult.reasonCodes(),
                asOfDate,
                asOf,
                coherenceKey
        ));
    }

    public record StockFundamentals(
            String symbol,
            String periodType,
            Integer fiscalYear,
            Integer fiscalQuarter,
            LocalDate periodStart,
            LocalDate periodEnd,
            String reportKind,
            String auditStatus,
            String currency,
            boolean restated,
            String basisPeriodLabel,
            List<FundamentalMetric> metrics,
            DataStatus dataStatus,
            List<String> reasonCodes,
            LocalDate tradingDate,
            Instant asOf,
            String coherenceKey
    ) {}

    public record FundamentalMetric(
            String metricCode,
            BigDecimal value,
            String unit,
            int displayPrecision,
            MetricApplicability applicability,
            String reasonCode
    ) {}
}
