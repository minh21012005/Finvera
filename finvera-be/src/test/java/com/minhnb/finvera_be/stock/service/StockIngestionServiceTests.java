package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.model.MarketTypes;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalReportMetricRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalReportRepository;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingDailyBar;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingFundamentalReport;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionStatus;
import com.minhnb.finvera_be.stock.service.StockIngestionService.MetricValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class StockIngestionServiceTests {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("finvera.security.owner.id", UUID::randomUUID);
        registry.add("finvera.security.owner.username", () -> "owner-" + UUID.randomUUID());
        registry.add("finvera.security.owner.password-hash",
                () -> new BCryptPasswordEncoder(4).encode(UUID.randomUUID().toString()));
    }

    @Autowired MarketInstrumentRepository instruments;
    @Autowired EquityDailyBarRepository dailyBars;
    @Autowired FundamentalReportRepository reports;
    @Autowired FundamentalSourceRetirementService retirement;
    @Autowired FundamentalReportMetricRepository reportMetrics;
    @Autowired StockIngestionService ingestion;

    @Test
    void acceptsAValidBarAndRejectsAnUnknownInstrument() {
        saveInstrument("STK01");
        var accepted = ingestion.ingestDailyBar(bar("FINVERA_FIXTURE", "STK01",
                LocalDate.of(2026, 8, 14), Instant.parse("2026-08-14T08:15:00Z"), false));
        assertThat(accepted.status()).isEqualTo(IngestionStatus.ACCEPTED);
        assertThat(accepted.barId()).isNotNull();

        var unknown = ingestion.ingestDailyBar(bar("FINVERA_FIXTURE", "ZZZUNKNOWN",
                LocalDate.of(2026, 8, 14), Instant.parse("2026-08-14T08:15:00Z"), false));
        assertThat(unknown.status()).isEqualTo(IngestionStatus.REJECTED);
        assertThat(unknown.reasonCode()).isEqualTo("UNKNOWN_INSTRUMENT");
    }

    @Test
    void rejectsInvertedOhlcBeforeItReachesStorage() {
        UUID instrumentId = saveInstrument("STK02");
        var invalid = ingestion.ingestDailyBar(new IncomingDailyBar(
                "FINVERA_FIXTURE", "STK02", LocalDate.of(2026, 8, 14), Instant.parse("2026-08-14T08:15:00Z"),
                new BigDecimal("100.000000"), new BigDecimal("90.000000"), new BigDecimal("95.000000"),
                new BigDecimal("92.000000"), 1_000_000L, null, "RAW", false));
        assertThat(invalid.status()).isEqualTo(IngestionStatus.REJECTED);
        assertThat(invalid.reasonCode()).isEqualTo("INVALID_OHLC");
        assertThat(dailyBars.countByInstrumentId(instrumentId)).isZero();
    }

    @Test
    void rejectsTheDeprecatedTcbsStockDailyBarSourceBeforeStorage() {
        UUID instrumentId = saveInstrument("STK11");

        var rejected = ingestion.ingestDailyBar(bar("TCBS_IFLASH_STOCK_DATA", "STK11",
                LocalDate.of(2026, 8, 24), Instant.parse("2026-08-24T03:00:00Z"), false));

        assertThat(rejected.status()).isEqualTo(IngestionStatus.REJECTED);
        assertThat(rejected.reasonCode()).isEqualTo("DEPRECATED_PROVIDER_INVALID_PRICE_UNIT");
        assertThat(dailyBars.countByInstrumentId(instrumentId)).isZero();
    }

    @Test
    void rejectsAnExactDuplicateSubmission() {
        UUID instrumentId = saveInstrument("STK03");
        var first = ingestion.ingestDailyBar(bar("FINVERA_FIXTURE", "STK03",
                LocalDate.of(2026, 8, 14), Instant.parse("2026-08-14T08:15:00Z"), false));
        assertThat(first.status()).isEqualTo(IngestionStatus.ACCEPTED);

        var duplicate = ingestion.ingestDailyBar(bar("FINVERA_FIXTURE", "STK03",
                LocalDate.of(2026, 8, 14), Instant.parse("2026-08-14T08:15:00Z"), false));
        assertThat(duplicate.status()).isEqualTo(IngestionStatus.DUPLICATE);
        assertThat(dailyBars.countByInstrumentId(instrumentId)).isEqualTo(1);
    }

    @Test
    void rejectsAnOlderUnflaggedSubmissionAsOutOfOrder() {
        saveInstrument("STK04");
        ingestion.ingestDailyBar(bar("FINVERA_FIXTURE", "STK04",
                LocalDate.of(2026, 8, 14), Instant.parse("2026-08-14T08:20:00Z"), false));

        var stale = ingestion.ingestDailyBar(bar("FINVERA_FIXTURE", "STK04",
                LocalDate.of(2026, 8, 14), Instant.parse("2026-08-14T08:10:00Z"), false));
        assertThat(stale.status()).isEqualTo(IngestionStatus.REJECTED);
        assertThat(stale.reasonCode()).isEqualTo("OUT_OF_ORDER");
    }

    @Test
    void aFlaggedCorrectionSupersedesTheAcceptedBarAndIncrementsRevision() {
        saveInstrument("STK05");
        var first = ingestion.ingestDailyBar(bar("FINVERA_FIXTURE", "STK05",
                LocalDate.of(2026, 8, 14), Instant.parse("2026-08-14T08:15:00Z"), false));

        var corrected = ingestion.ingestDailyBar(new IncomingDailyBar(
                "FINVERA_FIXTURE", "STK05", LocalDate.of(2026, 8, 14), Instant.parse("2026-08-14T08:15:00Z"),
                new BigDecimal("101.000000"), new BigDecimal("103.000000"), new BigDecimal("100.000000"),
                new BigDecimal("102.500000"), 1_200_000L, null, "RAW", true));

        assertThat(corrected.status()).isEqualTo(IngestionStatus.CORRECTED);
        assertThat(corrected.revision()).isEqualTo(2);

        var superseded = dailyBars.findById(first.barId()).orElseThrow();
        assertThat(superseded.isCurrent()).isFalse();
        var current = dailyBars.findById(corrected.barId()).orElseThrow();
        assertThat(current.isCurrent()).isTrue();
        assertThat(current.getClosePrice()).isEqualByComparingTo("102.500000");
    }

    @Test
    void detectsACrossSourceConflictAndRetainsBothProvenances() {
        UUID instrumentId = saveInstrument("STK06");
        ingestion.ingestDailyBar(bar("TCBS", "STK06",
                LocalDate.of(2026, 8, 14), Instant.parse("2026-08-14T08:15:00Z"), false));

        var vnstockBar = new IncomingDailyBar("VNSTOCK", "STK06", LocalDate.of(2026, 8, 14),
                Instant.parse("2026-08-15T01:00:00Z"), new BigDecimal("100.000000"), new BigDecimal("101.000000"),
                new BigDecimal("99.000000"), new BigDecimal("99.500000"), 900_000L, null, "RAW", false);
        var second = ingestion.ingestDailyBar(vnstockBar);
        assertThat(second.status()).isEqualTo(IngestionStatus.ACCEPTED);

        var conflict = ingestion.reconcileDailyBar(instrumentId, "STK06", LocalDate.of(2026, 8, 14));
        assertThat(conflict).isEqualTo(com.minhnb.finvera_be.market.domain.reconciliation.SourceReconciliationPolicy.Decision.SOURCE_CONFLICT);
        assertThat(dailyBars.countByInstrumentId(instrumentId)).isEqualTo(2);
    }

    @Test
    void detectsAProductionSourceFamilyConflictAndRetainsBothProvenances() {
        UUID instrumentId = saveInstrument("STK12");
        ingestion.ingestDailyBar(bar("TCBS_IFLASH_THESIS", "STK12",
                LocalDate.of(2026, 8, 24), Instant.parse("2026-08-24T03:00:00Z"), false));

        // A structurally valid bar (close inside [low, high]) whose close still
        // materially diverges from the TCBS bar's 100.5 — so acceptance succeeds
        // and the SOURCE_CONFLICT branch is genuinely exercised, not short-circuited
        // by OHLC validation rejecting the fixture.
        var vnstockBar = new IncomingDailyBar("VNSTOCK_KBS", "STK12", LocalDate.of(2026, 8, 24),
                Instant.parse("2026-08-24T08:00:00Z"), new BigDecimal("100.000000"), new BigDecimal("101.000000"),
                new BigDecimal("98.000000"), new BigDecimal("98.500000"), 900_000L, null, "RAW", false);
        var second = ingestion.ingestDailyBar(vnstockBar);
        assertThat(second.status()).isEqualTo(IngestionStatus.ACCEPTED);

        var conflict = ingestion.reconcileDailyBar(instrumentId, "STK12", LocalDate.of(2026, 8, 24));

        assertThat(conflict).isEqualTo(com.minhnb.finvera_be.market.domain.reconciliation.SourceReconciliationPolicy.Decision.SOURCE_CONFLICT);
        assertThat(dailyBars.countByInstrumentId(instrumentId)).isEqualTo(2);
    }

    @Test
    void restatementSupersedesAReportAndRequiresARestatementReason() {
        saveInstrument("STK07");
        var original = ingestion.ingestFundamentalReport(fundamentalReport("STK07", 2026, 1,
                Instant.parse("2026-04-25T02:05:00Z"), false, null));
        assertThat(original.status()).isEqualTo(IngestionStatus.ACCEPTED);

        var restated = ingestion.ingestFundamentalReport(fundamentalReport("STK07", 2026, 1,
                Instant.parse("2026-06-10T02:05:00Z"), true, "AUDIT_ADJUSTMENT"));
        assertThat(restated.status()).isEqualTo(IngestionStatus.CORRECTED);
        assertThat(restated.revision()).isEqualTo(2);

        var superseded = reports.findById(original.barId()).orElseThrow();
        assertThat(superseded.isCurrent()).isFalse();
        assertThat(reports.findById(restated.barId()).orElseThrow().getRestatementReason())
                .isEqualTo("AUDIT_ADJUSTMENT");
    }

    @Test
    void ingestFundamentalReportPersistsItsMetricValuesNotJustTheHeaderRow() {
        saveInstrument("STK09");
        var accepted = ingestion.ingestFundamentalReport(fundamentalReport("STK09", 2026, 3,
                Instant.parse("2026-10-25T02:05:00Z"), false, null));
        assertThat(accepted.status()).isEqualTo(IngestionStatus.ACCEPTED);

        var persistedMetrics = reportMetrics.findByReportId(accepted.barId());
        assertThat(persistedMetrics).isNotEmpty();
        assertThat(persistedMetrics).anySatisfy(m -> {
            assertThat(m.getMetricCode()).isEqualTo("REVENUE");
            assertThat(m.getValue()).isEqualByComparingTo(new BigDecimal("1000000000.000000"));
            assertThat(m.getApplicability()).isEqualTo("DEFINED");
        });
    }

    @Test
    void rejectsAFundamentalReportWithAnUnrecognizedApplicabilityValue() {
        UUID instrumentId = saveInstrument("STK10");
        var incoming = new IncomingFundamentalReport("FINVERA_FIXTURE", "STK10", "QUARTER", 2026, 3,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30), "CONSOLIDATED", "REVIEWED", "VND",
                1, "fundamental-metric-catalog-v1", Instant.parse("2026-10-25T02:05:00Z"),
                List.of(new MetricValue("REVENUE", new BigDecimal("1.000000"), "NOT_A_REAL_STATE", null)),
                false, null);

        var result = ingestion.ingestFundamentalReport(incoming);

        assertThat(result.status()).isEqualTo(IngestionStatus.REJECTED);
        assertThat(result.reasonCode()).isEqualTo("INVALID_METRIC");
        assertThat(reports.findFirstByInstrumentIdAndCurrentTrueOrderByPeriodEndDesc(instrumentId)).isEmpty();
    }

    @Test
    void rejectsADuplicateFundamentalReportSubmission() {
        saveInstrument("STK08");
        ingestion.ingestFundamentalReport(fundamentalReport("STK08", 2026, 2,
                Instant.parse("2026-07-28T02:05:00Z"), false, null));
        var duplicate = ingestion.ingestFundamentalReport(fundamentalReport("STK08", 2026, 2,
                Instant.parse("2026-07-28T02:05:00Z"), false, null));
        assertThat(duplicate.status()).isEqualTo(IngestionStatus.DUPLICATE);
    }

    private UUID saveInstrument(String symbol) {
        UUID id = UUID.randomUUID();
        instruments.save(new MarketInstrumentEntity(id, null, MarketTypes.Venue.HOSE.name(), symbol, "EQUITY",
                LocalDate.of(2010, 1, 1), null, "ACTIVE", "FINVERA_FIXTURE", "v1"));
        return id;
    }

    private static IncomingDailyBar bar(String source, String symbol, LocalDate tradingDate, Instant observedAt,
            boolean isCorrection) {
        return new IncomingDailyBar(source, symbol, tradingDate, observedAt,
                new BigDecimal("100.000000"), new BigDecimal("101.000000"), new BigDecimal("99.000000"),
                new BigDecimal("100.500000"), 1_000_000L, null, "RAW", isCorrection);
    }

    private static IncomingFundamentalReport fundamentalReport(String symbol, int fiscalYear, int fiscalQuarter,
            Instant observedAt, boolean isRestatement, String restatementReason) {
        return new IncomingFundamentalReport("FINVERA_FIXTURE", symbol, "QUARTER", fiscalYear, fiscalQuarter,
                LocalDate.of(fiscalYear, 1, 1), LocalDate.of(fiscalYear, 3, 31), "CONSOLIDATED", "REVIEWED", "VND",
                1, "fundamental-metric-catalog-v1", observedAt,
                List.of(new MetricValue("REVENUE", new BigDecimal("1000000000.000000"), "DEFINED", null)),
                isRestatement, restatementReason);
    }

    @Test
    void aNewerSourceSupersedesTheOlderSourcesCurrentReportForTheSamePeriod() {
        // Feature 018 (contract vci-fundamentals-v1 I-1): VCI rows retire the KBS rows period by period.
        saveInstrument("STK18");
        var kbs = ingestion.ingestFundamentalReport(new IncomingFundamentalReport("VNSTOCK_KBS", "STK18", "QUARTER", 2026, 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "UNKNOWN", "UNKNOWN", "VND", 1, "fundamental-metric-catalog-v1",
                Instant.parse("2026-05-15T00:00:00Z"),
                List.of(new MetricValue("REVENUE", new BigDecimal("1000000000.000000"), "DEFINED", null)), false, null));
        assertThat(kbs.status()).isEqualTo(IngestionStatus.ACCEPTED);

        // Older observedAt than the KBS row (the importer's period-end + lag convention) must NOT be OUT_OF_ORDER across sources.
        var vci = ingestion.ingestFundamentalReport(new IncomingFundamentalReport("VNSTOCK_VCI", "STK18", "QUARTER", 2026, 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "UNKNOWN", "UNKNOWN", "VND", 1, "fundamental-metric-catalog-v1",
                Instant.parse("2026-05-15T00:00:00Z"),
                List.of(new MetricValue("REVENUE", new BigDecimal("1200000000.000000"), "DEFINED", null)), false, null));
        assertThat(vci.status()).isEqualTo(IngestionStatus.CORRECTED);
        assertThat(vci.revision()).isEqualTo(2);

        var previous = reports.findById(kbs.barId()).orElseThrow();
        assertThat(previous.isCurrent()).isFalse();
        var current = reports.findById(vci.barId()).orElseThrow();
        assertThat(current.isCurrent()).isTrue();
        assertThat(current.getSource()).isEqualTo("VNSTOCK_VCI");
        assertThat(current.getRestatementReason()).isEqualTo(StockIngestionService.SOURCE_SUPERSEDED);

        // Same source, older observation: still rejected as out of order.
        var stale = ingestion.ingestFundamentalReport(new IncomingFundamentalReport("VNSTOCK_VCI", "STK18", "QUARTER", 2026, 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "UNKNOWN", "UNKNOWN", "VND", 1, "fundamental-metric-catalog-v1",
                Instant.parse("2026-05-01T00:00:00Z"),
                List.of(new MetricValue("REVENUE", new BigDecimal("1100000000.000000"), "DEFINED", null)), false, null));
        assertThat(stale.status()).isEqualTo(IngestionStatus.REJECTED);
        assertThat(stale.reasonCode()).isEqualTo("OUT_OF_ORDER");
    }

    @Test
    void retiringASourceMarksItsCurrentReportsNotCurrentWithoutDeletingThem() {
        // Feature 018: KBS rows for periods VCI does not serve must not stay current with the wrong period.
        saveInstrument("STK19");
        var kbs = ingestion.ingestFundamentalReport(new IncomingFundamentalReport("VNSTOCK_KBS", "STK19", "QUARTER", 2025, 4,
                LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31), "UNKNOWN", "UNKNOWN", "VND", 1, "fundamental-metric-catalog-v1",
                Instant.parse("2026-02-14T00:00:00Z"),
                List.of(new MetricValue("REVENUE", new BigDecimal("500000000.000000"), "DEFINED", null)), false, null));
        var vci = ingestion.ingestFundamentalReport(new IncomingFundamentalReport("VNSTOCK_VCI", "STK19", "ANNUAL", 2025, null,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), "UNKNOWN", "UNKNOWN", "VND", 1, "fundamental-metric-catalog-v1",
                Instant.parse("2026-02-14T00:00:00Z"),
                List.of(new MetricValue("REVENUE", new BigDecimal("2000000000.000000"), "DEFINED", null)), false, null));

        var summary = retirement.retire("VNSTOCK_KBS");

        assertThat(summary.retiredReports()).isGreaterThanOrEqualTo(1);
        var retired = reports.findById(kbs.barId()).orElseThrow();
        assertThat(retired.isCurrent()).isFalse();
        assertThat(retired.getRestatementReason()).isEqualTo(FundamentalSourceRetirementService.SOURCE_RETIRED);
        assertThat(reports.findById(vci.barId()).orElseThrow().isCurrent()).isTrue();
        assertThat(reports.findAllBySourceAndCurrentTrue("VNSTOCK_KBS")).isEmpty();
    }

    @Test
    void retireAllExceptKeepsThePrimarySourceAndIsIdempotent() {
        saveInstrument("STK20");
        var kbs = ingestion.ingestFundamentalReport(new IncomingFundamentalReport("VNSTOCK_KBS", "STK20", "QUARTER", 2025, 3,
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 9, 30), "UNKNOWN", "UNKNOWN", "VND", 1, "fundamental-metric-catalog-v1",
                Instant.parse("2025-11-14T00:00:00Z"),
                List.of(new MetricValue("REVENUE", new BigDecimal("400000000.000000"), "DEFINED", null)), false, null));
        var vci = ingestion.ingestFundamentalReport(new IncomingFundamentalReport("VNSTOCK_VCI", "STK20", "ANNUAL", 2025, null,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), "UNKNOWN", "UNKNOWN", "VND", 1, "fundamental-metric-catalog-v1",
                Instant.parse("2026-02-14T00:00:00Z"),
                List.of(new MetricValue("REVENUE", new BigDecimal("1600000000.000000"), "DEFINED", null)), false, null));

        var first = retirement.retireAllExcept("VNSTOCK_VCI");
        var second = retirement.retireAllExcept("VNSTOCK_VCI");

        assertThat(first.retiredReports()).isGreaterThanOrEqualTo(1);
        assertThat(second.retiredReports()).isZero();                       // idempotent: nothing left to retire
        assertThat(reports.findById(kbs.barId()).orElseThrow().isCurrent()).isFalse();
        assertThat(reports.findById(vci.barId()).orElseThrow().isCurrent()).isTrue();
        assertThat(reports.findAllByCurrentTrueAndSourceNot("VNSTOCK_VCI")).isEmpty();
    }
}
