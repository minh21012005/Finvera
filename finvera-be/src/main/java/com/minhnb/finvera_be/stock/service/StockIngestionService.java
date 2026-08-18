package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.market.domain.reconciliation.SourceReconciliationPolicy.AdjustmentStatus;
import com.minhnb.finvera_be.market.domain.reconciliation.SourceReconciliationPolicy.Decision;
import com.minhnb.finvera_be.market.domain.reconciliation.SourceReconciliationPolicy.Fact;
import com.minhnb.finvera_be.market.service.IngestionRecordService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.SourceReconciliationService;
import com.minhnb.finvera_be.stock.domain.fundamentals.FundamentalReportAcceptance;
import com.minhnb.finvera_be.stock.domain.fundamentals.FundamentalReportAcceptance.AcceptanceResult;
import com.minhnb.finvera_be.stock.domain.fundamentals.FundamentalReportAcceptance.AcceptedMetric;
import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import com.minhnb.finvera_be.stock.entity.FundamentalReportEntity;
import com.minhnb.finvera_be.stock.entity.FundamentalReportMetricEntity;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalReportMetricRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalReportRepository;
import com.minhnb.finvera_be.stock.service.StockObservabilityService.StockDataset;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transactional acceptance boundary for Feature 002's own datasets (daily
 * bars and fundamental reports), enforcing acceptance checks A-2, A-6, A-8,
 * A-9, A-10, and A-11 from contracts/stock-data-provider.md. Corporate
 * action ingestion follows the same shape and is added when its gate (G-02)
 * closes; it is out of scope for the fixture-mode foundation built here.
 */
@Service
public class StockIngestionService {

    private static final String DAILY_BAR_DATASET = "DAILY_BAR";
    private static final String FUNDAMENTAL_REPORT_DATASET = "FUNDAMENTAL_REPORT";
    // Defence in depth for A-11: a provider field that looks like a credential must
    // never reach a stored column, even though the typed DTO leaves little room for one.
    private static final Pattern CREDENTIAL_SHAPED = Pattern.compile(
            "(?i).*(api[_-]?key|token|secret|password|bearer\\s+[a-z0-9._-]{10,}).*");

    private final MarketReferenceDataService referenceData;
    private final IngestionRecordService ingestionRecords;
    private final EquityDailyBarRepository dailyBars;
    private final FundamentalReportRepository fundamentalReports;
    private final FundamentalReportMetricRepository fundamentalReportMetrics;
    private final SourceReconciliationService reconciliation;
    private final StockObservabilityService observability;
    private final FundamentalReportAcceptance metricAcceptance = new FundamentalReportAcceptance();
    private final Clock clock;

    public StockIngestionService(
            MarketReferenceDataService referenceData,
            IngestionRecordService ingestionRecords,
            EquityDailyBarRepository dailyBars,
            FundamentalReportRepository fundamentalReports,
            FundamentalReportMetricRepository fundamentalReportMetrics,
            SourceReconciliationService reconciliation,
            StockObservabilityService observability,
            Clock clock) {
        this.referenceData = referenceData;
        this.ingestionRecords = ingestionRecords;
        this.dailyBars = dailyBars;
        this.fundamentalReports = fundamentalReports;
        this.fundamentalReportMetrics = fundamentalReportMetrics;
        this.reconciliation = reconciliation;
        this.observability = observability;
        this.clock = clock;
    }

    private void recordTelemetryAfterCommit(String source, StockDataset dataset, IngestionResult result, Instant observedAt) {
        Runnable recorder = () -> observability.recordIngestion(source, dataset, result, observedAt);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            recorder.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                recorder.run();
            }
        });
    }

    @Transactional
    public IngestionResult ingestDailyBar(IncomingDailyBar incoming) {
        Objects.requireNonNull(incoming, "incoming");
        IngestionResult result = ingestDailyBarInternal(incoming);
        recordTelemetryAfterCommit(incoming.source(), StockDataset.CHART, result, incoming.observedAt());
        return result;
    }

    private IngestionResult ingestDailyBarInternal(IncomingDailyBar incoming) {
        Instant ingestedAt = clock.instant();

        if (isCredentialShaped(incoming.source()) || isCredentialShaped(incoming.adjustmentStatus())) {
            return new IngestionResult(IngestionStatus.REJECTED, "PAYLOAD_REJECTED", null, null);
        }

        var instrument = referenceData.findActiveInstrumentBySymbol(incoming.symbol());
        if (instrument.isEmpty()) {
            return new IngestionResult(IngestionStatus.REJECTED, "UNKNOWN_INSTRUMENT", null, null);
        }

        String ohlcReason = validateOhlc(incoming);
        if (ohlcReason != null) {
            return new IngestionResult(IngestionStatus.REJECTED, ohlcReason, null, null);
        }

        String payloadHash = hashDailyBar(incoming);
        if (ingestionRecords.isDuplicate(incoming.source(), DAILY_BAR_DATASET, incoming.symbol(),
                incoming.tradingDate(), incoming.observedAt(), payloadHash)) {
            return new IngestionResult(IngestionStatus.DUPLICATE, "DUPLICATE", null, null);
        }

        var latestAccepted = ingestionRecords.findLatestAccepted(
                incoming.source(), DAILY_BAR_DATASET, incoming.symbol(), incoming.tradingDate());
        if (!incoming.isCorrection() && latestAccepted.isPresent()
                && latestAccepted.orElseThrow().observedAt().isAfter(incoming.observedAt())) {
            return new IngestionResult(IngestionStatus.REJECTED, "OUT_OF_ORDER", null, null);
        }

        UUID instrumentId = instrument.orElseThrow().instrumentId();
        Optional<EquityDailyBarEntity> currentBar = dailyBars.findFirstByInstrumentIdAndTradingDateAndSourceAndCurrentTrue(
                instrumentId, incoming.tradingDate(), incoming.source());
        boolean isCorrection = currentBar.isPresent();
        int revision = currentBar.map(bar -> bar.getRevision() + 1).orElse(1);

        UUID ingestionRecordId = ingestionRecords.recordAccepted(incoming.source(), DAILY_BAR_DATASET,
                incoming.symbol(), incoming.tradingDate(), incoming.observedAt(), ingestedAt, payloadHash, null);

        if (isCorrection) {
            // saveAndFlush forces the UPDATE to commit before the INSERT below: Hibernate
            // groups same-type actions by kind (all inserts, then all updates) within one
            // flush, and the partial unique index requires the demotion to land first.
            EquityDailyBarEntity previous = currentBar.orElseThrow();
            previous.markSuperseded();
            dailyBars.saveAndFlush(previous);
        }

        UUID barId = UUID.randomUUID();
        dailyBars.save(new EquityDailyBarEntity(barId, instrumentId, ingestionRecordId, null,
                incoming.tradingDate(), incoming.open(), incoming.high(), incoming.low(), incoming.close(),
                null, null, incoming.adjustmentStatus(), incoming.volume(), incoming.valueVnd(), incoming.source(),
                incoming.observedAt(), ingestedAt, revision, true,
                currentBar.map(EquityDailyBarEntity::getId).orElse(null), null));

        return new IngestionResult(
                isCorrection ? IngestionStatus.CORRECTED : IngestionStatus.ACCEPTED, null, barId, revision);
    }

    /** Cross-source conflict check for a trading date accepted from more than one source (DATA-010). */
    @Transactional
    public Decision reconcileDailyBar(UUID instrumentId, String symbol, LocalDate tradingDate) {
        Objects.requireNonNull(instrumentId, "instrumentId");
        Objects.requireNonNull(tradingDate, "tradingDate");
        var tcbsBar = dailyBars.findFirstByInstrumentIdAndTradingDateAndSourceAndCurrentTrue(
                instrumentId, tradingDate, "TCBS");
        var vnstockBar = dailyBars.findFirstByInstrumentIdAndTradingDateAndSourceAndCurrentTrue(
                instrumentId, tradingDate, "VNSTOCK");
        var result = reconciliation.reconcile(new SourceReconciliationService.Command(
                instrumentId, tradingDate,
                tcbsBar.map(EquityDailyBarEntity::getIngestionRecordId).orElse(nilUuid()),
                vnstockBar.map(EquityDailyBarEntity::getIngestionRecordId).orElse(nilUuid()),
                tcbsBar.map(StockIngestionService::toFact).orElse(null),
                vnstockBar.map(StockIngestionService::toFact).orElse(null)));
        return result.decision();
    }

    @Transactional
    public IngestionResult ingestFundamentalReport(IncomingFundamentalReport incoming) {
        Objects.requireNonNull(incoming, "incoming");
        IngestionResult result = ingestFundamentalReportInternal(incoming);
        recordTelemetryAfterCommit(incoming.source(), StockDataset.FUNDAMENTALS, result, incoming.observedAt());
        return result;
    }

    private IngestionResult ingestFundamentalReportInternal(IncomingFundamentalReport incoming) {
        Instant ingestedAt = clock.instant();

        if (isCredentialShaped(incoming.source())) {
            return new IngestionResult(IngestionStatus.REJECTED, "PAYLOAD_REJECTED", null, null);
        }
        var instrument = referenceData.findActiveInstrumentBySymbol(incoming.symbol());
        if (instrument.isEmpty()) {
            return new IngestionResult(IngestionStatus.REJECTED, "UNKNOWN_INSTRUMENT", null, null);
        }
        boolean quarterConsistent = "ANNUAL".equals(incoming.periodType()) == (incoming.fiscalQuarter() == null);
        if (!quarterConsistent || incoming.periodEnd().isBefore(incoming.periodStart())) {
            return new IngestionResult(IngestionStatus.REJECTED, "INVALID_PERIOD", null, null);
        }
        if (incoming.isRestatement()
                && (incoming.restatementReason() == null || incoming.restatementReason().isBlank())) {
            return new IngestionResult(IngestionStatus.REJECTED, "PAYLOAD_REJECTED", null, null);
        }

        // FR-007/DATA-007: validate and normalize metric values (allowlisted
        // codes, unit-scale multiplication, three-state applicability) before
        // any row is written — the same fail-fast-before-write shape as
        // validateOhlc for daily bars.
        AcceptanceResult metricAcceptanceResult = metricAcceptance.accept(toAcceptanceInput(incoming));
        if (!metricAcceptanceResult.accepted()) {
            return new IngestionResult(IngestionStatus.REJECTED, metricAcceptanceResult.reasonCode(), null, null);
        }

        String subjectKey = incoming.symbol() + "|" + incoming.periodType() + "|" + incoming.fiscalYear()
                + "|" + incoming.fiscalQuarter() + "|" + incoming.reportKind();
        String payloadHash = hashFundamentalReport(incoming);
        if (ingestionRecords.isDuplicate(incoming.source(), FUNDAMENTAL_REPORT_DATASET, subjectKey,
                incoming.periodEnd(), incoming.observedAt(), payloadHash)) {
            return new IngestionResult(IngestionStatus.DUPLICATE, "DUPLICATE", null, null);
        }

        UUID instrumentId = instrument.orElseThrow().instrumentId();
        Optional<FundamentalReportEntity> currentReport = fundamentalReports
                .findFirstByInstrumentIdAndPeriodTypeAndFiscalYearAndFiscalQuarterAndReportKindAndSourceAndCurrentTrue(
                        instrumentId, incoming.periodType(), (short) incoming.fiscalYear(),
                        incoming.fiscalQuarter() == null ? null : incoming.fiscalQuarter().shortValue(),
                        incoming.reportKind(), incoming.source());

        if (!incoming.isRestatement() && currentReport.isPresent()
                && currentReport.orElseThrow().getObservedAt().isAfter(incoming.observedAt())) {
            return new IngestionResult(IngestionStatus.REJECTED, "OUT_OF_ORDER", null, null);
        }

        boolean isRestatement = currentReport.isPresent();
        int revision = currentReport.map(report -> report.getRevision() + 1).orElse(1);
        UUID ingestionRecordId = ingestionRecords.recordAccepted(incoming.source(), FUNDAMENTAL_REPORT_DATASET,
                subjectKey, incoming.periodEnd(), incoming.observedAt(), ingestedAt, payloadHash, null);

        if (isRestatement) {
            // Same flush-ordering reason as ingestDailyBar's saveAndFlush.
            FundamentalReportEntity previous = currentReport.orElseThrow();
            previous.markSuperseded();
            fundamentalReports.saveAndFlush(previous);
        }

        UUID reportId = UUID.randomUUID();
        fundamentalReports.save(new FundamentalReportEntity(reportId, instrumentId, ingestionRecordId,
                incoming.periodType(), (short) incoming.fiscalYear(),
                incoming.fiscalQuarter() == null ? null : incoming.fiscalQuarter().shortValue(),
                incoming.periodStart(), incoming.periodEnd(), incoming.reportKind(), incoming.auditStatus(),
                incoming.currency(), incoming.unitScale(), incoming.catalogVersion(), null, incoming.observedAt(),
                ingestedAt, incoming.source(), revision, true,
                currentReport.map(FundamentalReportEntity::getId).orElse(null),
                isRestatement ? incoming.restatementReason() : null));

        for (AcceptedMetric metric : metricAcceptanceResult.metrics()) {
            fundamentalReportMetrics.save(new FundamentalReportMetricEntity(
                    reportId, metric.metricCode(), metric.value(), metric.applicability().name(),
                    metric.qualityReason()));
        }

        return new IngestionResult(
                isRestatement ? IngestionStatus.CORRECTED : IngestionStatus.ACCEPTED, null, reportId, revision);
    }

    private static FundamentalReportAcceptance.ReportInput toAcceptanceInput(IncomingFundamentalReport incoming) {
        List<FundamentalReportAcceptance.MetricInput> metricInputs = incoming.metrics().stream()
                .map(m -> new FundamentalReportAcceptance.MetricInput(
                        m.metricCode(), m.value(), m.applicability(), m.qualityReason()))
                .toList();
        return new FundamentalReportAcceptance.ReportInput(
                incoming.periodType(), incoming.fiscalYear(), incoming.fiscalQuarter(),
                incoming.periodStart(), incoming.periodEnd(), incoming.reportKind(), incoming.auditStatus(),
                incoming.currency(), incoming.unitScale(), incoming.catalogVersion(), metricInputs);
    }

    private static String validateOhlc(IncomingDailyBar bar) {
        if (bar.high().compareTo(bar.low()) < 0) {
            return "INVALID_OHLC";
        }
        if (bar.high().compareTo(bar.open()) < 0 || bar.high().compareTo(bar.close()) < 0) {
            return "INVALID_OHLC";
        }
        if (bar.low().compareTo(bar.open()) > 0 || bar.low().compareTo(bar.close()) > 0) {
            return "INVALID_OHLC";
        }
        if (bar.open().signum() < 0 || bar.high().signum() < 0 || bar.low().signum() < 0
                || bar.close().signum() < 0 || (bar.volume() != null && bar.volume() < 0)) {
            return "VALUE_OUT_OF_BOUNDS";
        }
        return null;
    }

    private static boolean isCredentialShaped(String value) {
        return value != null && CREDENTIAL_SHAPED.matcher(value).matches();
    }

    private static Fact toFact(EquityDailyBarEntity bar) {
        AdjustmentStatus status = "RAW".equals(bar.getAdjustmentStatus())
                ? AdjustmentStatus.RAW
                : AdjustmentStatus.UNKNOWN;
        return new Fact(bar.getClosePrice(), bar.getOpenPrice(), status);
    }

    private static UUID nilUuid() {
        return new UUID(0L, 0L);
    }

    private static String hashDailyBar(IncomingDailyBar bar) {
        return sha256(String.join("|", bar.source(), bar.symbol(), bar.tradingDate().toString(),
                bar.observedAt().toString(), canonicalDecimal(bar.open()), canonicalDecimal(bar.high()),
                canonicalDecimal(bar.low()), canonicalDecimal(bar.close()),
                bar.volume() == null ? "" : bar.volume().toString(), bar.adjustmentStatus()));
    }

    private static String hashFundamentalReport(IncomingFundamentalReport report) {
        StringBuilder metricsPart = new StringBuilder();
        report.metrics().forEach(metric -> metricsPart.append(metric.metricCode()).append('=')
                .append(metric.value() == null ? "" : canonicalDecimal(metric.value())).append(';'));
        return sha256(String.join("|", report.source(), report.symbol(), report.periodType(),
                String.valueOf(report.fiscalYear()), String.valueOf(report.fiscalQuarter()),
                report.reportKind(), report.observedAt().toString(), metricsPart.toString()));
    }

    private static String canonicalDecimal(BigDecimal value) {
        if (value == null) {
            return "";
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private static String sha256(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public enum IngestionStatus {
        ACCEPTED, DUPLICATE, REJECTED, CORRECTED
    }

    public record IngestionResult(IngestionStatus status, String reasonCode, UUID barId, Integer revision) {
    }

    public record IncomingDailyBar(
            String source,
            String symbol,
            LocalDate tradingDate,
            Instant observedAt,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            Long volume,
            BigDecimal valueVnd,
            String adjustmentStatus,
            boolean isCorrection) {
    }

    public record IncomingFundamentalReport(
            String source,
            String symbol,
            String periodType,
            int fiscalYear,
            Integer fiscalQuarter,
            LocalDate periodStart,
            LocalDate periodEnd,
            String reportKind,
            String auditStatus,
            String currency,
            int unitScale,
            String catalogVersion,
            Instant observedAt,
            List<MetricValue> metrics,
            boolean isRestatement,
            String restatementReason) {
    }

    public record MetricValue(String metricCode, BigDecimal value, String applicability, String qualityReason) {
    }
}
