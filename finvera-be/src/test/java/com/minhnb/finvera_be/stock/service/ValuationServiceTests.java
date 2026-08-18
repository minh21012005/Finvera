package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.model.MarketTypes;
import com.minhnb.finvera_be.market.domain.reconciliation.SourceReconciliationPolicy.Decision;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.entity.ValuationAssessmentEntity;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.ValuationAssessmentInputRepository;
import com.minhnb.finvera_be.stock.repository.ValuationAssessmentRepository;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingDailyBar;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingFundamentalReport;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionStatus;
import com.minhnb.finvera_be.stock.service.StockIngestionService.MetricValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

/**
 * T052 [FR-008, FR-009, FR-010, FR-014, DATA-009, DATA-010; NFR-003]
 * Application integration tests for {@link ValuationService}'s persistence
 * of {@code valuation_assessment}/{@code valuation_metric}/
 * {@code valuation_assessment_input}: until this test existed, the service
 * computed a fresh {@code AssessmentResult} on every read and never wrote it
 * anywhere — the repositories T010 built for these three tables were wired
 * into the constructor and never called. These tests exercise the real
 * persistence, correction, and cross-source-conflict paths this task's own
 * Verify clause requires.
 */
@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class ValuationServiceTests {

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
    @Autowired EquityProfileRepository profiles;
    @Autowired StockIngestionService ingestion;
    @Autowired ValuationService valuation;
    @Autowired ValuationAssessmentRepository assessments;
    @Autowired ValuationAssessmentInputRepository assessmentInputs;

    @Test
    void unknownSymbolIsAbsentNotFabricated() {
        assertThat(valuation.findBySymbol("ZZZUNKNOWN")).isEmpty();
    }

    @Test
    void persistsAWithheldAssessmentWithAllOrNothingFieldsAndIsIdempotentOnReplay() {
        UUID instrumentId = saveInstrument("STV01");
        saveProfile(instrumentId, 1_000_000_000L);
        seedDailyBar("STV01", "FINVERA_FIXTURE", LocalDate.of(2026, 8, 14), new BigDecimal("50000.000000"));
        seedFourQuartersOfFundamentals("STV01", 2025, new BigDecimal("500.000000"));

        var first = valuation.findBySymbol("STV01").orElseThrow();
        // Fewer than 500 own-history points and no sector series: both bases
        // are unavailable, so the assessment is withheld, not fabricated.
        assertThat(first.published()).isFalse();
        assertThat(first.classification()).isNull();
        assertThat(first.score()).isNull();
        assertThat(first.displayedScore()).isNull();
        assertThat(first.confidence()).isNull();
        assertThat(first.reasonCodes()).contains("NO_COMPARISON_BASIS");

        ValuationAssessmentEntity persisted = assessments
                .findFirstByInstrumentIdAndRuleVersionAndCurrentTrueOrderByAsOfTradingDateDesc(
                        instrumentId, valuationRuleVersion())
                .orElseThrow();
        assertThat(persisted.getClassification()).isNull();
        assertThat(persisted.getScore()).isNull();
        assertThat(assessmentInputs.findByAssessmentId(persisted.getId())).isNotEmpty();

        // A second read of unchanged accepted inputs must not create a new revision.
        valuation.findBySymbol("STV01");
        var stillCurrent = assessments
                .findFirstByInstrumentIdAndRuleVersionAndCurrentTrueOrderByAsOfTradingDateDesc(
                        instrumentId, valuationRuleVersion())
                .orElseThrow();
        assertThat(stillCurrent.getId()).isEqualTo(persisted.getId());
    }

    @Test
    void correctingThePriceBarProducesANewAssessmentRevisionWhilePreviousStaysQueryable() {
        UUID instrumentId = saveInstrument("STV02");
        saveProfile(instrumentId, 1_000_000_000L);
        LocalDate tradingDate = LocalDate.of(2026, 8, 14);
        seedDailyBar("STV02", "FINVERA_FIXTURE", tradingDate, new BigDecimal("50000.000000"));
        seedFourQuartersOfFundamentals("STV02", 2025, new BigDecimal("500.000000"));

        var before = valuation.findBySymbol("STV02").orElseThrow();
        UUID previousAssessmentId = assessments
                .findFirstByInstrumentIdAndRuleVersionAndCurrentTrueOrderByAsOfTradingDateDesc(
                        instrumentId, valuationRuleVersion())
                .orElseThrow().getId();
        assertThat(before.coherenceKey()).isNotBlank();

        var corrected = ingestion.ingestDailyBar(new IncomingDailyBar("FINVERA_FIXTURE", "STV02", tradingDate,
                Instant.parse("2026-08-14T09:00:00Z"), new BigDecimal("60000.000000"), new BigDecimal("60500.000000"),
                new BigDecimal("59500.000000"), new BigDecimal("60000.000000"), 900_000L, null, "RAW", true));
        assertThat(corrected.status()).isEqualTo(IngestionStatus.CORRECTED);

        var after = valuation.findBySymbol("STV02").orElseThrow();
        UUID newAssessmentId = assessments
                .findFirstByInstrumentIdAndRuleVersionAndCurrentTrueOrderByAsOfTradingDateDesc(
                        instrumentId, valuationRuleVersion())
                .orElseThrow().getId();

        assertThat(newAssessmentId).isNotEqualTo(previousAssessmentId);
        assertThat(after.coherenceKey()).isNotEqualTo(before.coherenceKey());
        // The superseded revision remains queryable, not deleted (DATA-006/DATA-009).
        assertThat(assessments.findById(previousAssessmentId)).isPresent();
        assertThat(assessments.findById(previousAssessmentId).orElseThrow().isCurrent()).isFalse();
        assertThat(assessments.findById(newAssessmentId).orElseThrow().getSupersedesId())
                .isEqualTo(previousAssessmentId);
    }

    @Test
    void aSourceConflictOnTheConsumedPriceBarWithholdsWithSourceConflictReason() {
        saveInstrument("STV03");
        saveProfile(resolveInstrumentId("STV03"), 1_000_000_000L);
        LocalDate tradingDate = LocalDate.of(2026, 8, 14);
        seedDailyBar("STV03", "TCBS", tradingDate, new BigDecimal("50000.000000"));
        seedFourQuartersOfFundamentals("STV03", 2025, new BigDecimal("500.000000"));

        var vnstockResult = ingestion.ingestDailyBar(new IncomingDailyBar("VNSTOCK", "STV03", tradingDate,
                Instant.parse("2026-08-15T01:00:00Z"), new BigDecimal("80000.000000"), new BigDecimal("81000.000000"),
                new BigDecimal("79000.000000"), new BigDecimal("80500.000000"), 900_000L, null, "RAW", false));
        assertThat(vnstockResult.status()).isEqualTo(IngestionStatus.ACCEPTED);
        assertThat(ingestion.reconcileDailyBar(resolveInstrumentId("STV03"), "STV03", tradingDate))
                .isEqualTo(Decision.SOURCE_CONFLICT);

        var result = valuation.findBySymbol("STV03").orElseThrow();
        assertThat(result.published()).isFalse();
        assertThat(result.reasonCodes()).contains("SOURCE_CONFLICT");
    }

    /**
     * T068 [SC-003]: recomputation from the exact accepted rows a persisted
     * {@code valuation_assessment} recorded must reproduce its stored decimals
     * exactly. Crossing the {@code H_min = 500} own-history floor (unlike the
     * other tests in this class, which exercise the withheld path) is what
     * makes {@code score}/{@code confidence} non-null here, so this is the one
     * scenario that actually proves decimal-for-decimal replay, not merely
     * "the same row was reused."
     */
    @Test
    void replayingAPublishedAssessmentFromItsAcceptedInputsReproducesTheStoredDecimalsExactly() {
        UUID instrumentId = saveInstrument("STV04");
        saveProfile(instrumentId, 1_000_000_000L);
        // Two fiscal years of quarters: the 2024 set (observed by ~2025-01-20)
        // makes EPS_TTM definable for every daily bar from 2025-01-30 onward;
        // the 2025 set keeps "as of today" fundamentals within the DELAYED
        // (not STALE) freshness floor so the assessment can still publish.
        seedFourQuartersOfFundamentals("STV04", 2024, new BigDecimal("500.000000"));
        seedFourQuartersOfFundamentals("STV04", 2025, new BigDecimal("520.000000"));
        LocalDate historyStart = LocalDate.of(2025, 1, 30);
        LocalDate today = LocalDate.now();
        for (LocalDate d = historyStart; !d.isAfter(today); d = d.plusDays(1)) {
            long dayIndex = java.time.temporal.ChronoUnit.DAYS.between(historyStart, d);
            BigDecimal close = new BigDecimal("50000.000000").add(BigDecimal.valueOf(dayIndex));
            seedDailyBar("STV04", "FINVERA_FIXTURE", d, close);
        }

        var first = valuation.findBySymbol("STV04").orElseThrow();
        assertThat(first.published()).isTrue();
        assertThat(first.score()).isNotNull();

        ValuationAssessmentEntity persisted = assessments
                .findFirstByInstrumentIdAndRuleVersionAndCurrentTrueOrderByAsOfTradingDateDesc(
                        instrumentId, valuationRuleVersion())
                .orElseThrow();

        // valuation_assessment.score is numeric(6,3) (data-model.md): storage
        // precision is 3 decimal places, distinct from the scale-12 precision
        // the domain engine carries internally (U-1). Compare at the column's
        // own precision, not bit-for-bit against the unrounded in-memory value.
        var replay = valuation.findBySymbol("STV04").orElseThrow();
        assertThat(replay.score().setScale(3, java.math.RoundingMode.HALF_UP))
                .isEqualByComparingTo(persisted.getScore());
        assertThat(replay.displayedScore()).isEqualTo(persisted.getDisplayedScore().intValue());
        assertThat(replay.confidence()).isEqualTo(persisted.getConfidence().intValue());
        assertThat(replay.classification().name()).isEqualTo(persisted.getClassification());
    }

    private static String valuationRuleVersion() {
        return com.minhnb.finvera_be.stock.domain.valuation.ValuationV1.RULE_VERSION;
    }

    private UUID resolveInstrumentId(String symbol) {
        return instruments.findAll().stream()
                .filter(i -> symbol.equals(i.getSymbol())).findFirst().orElseThrow().getId();
    }

    private void saveProfile(UUID instrumentId, long sharesOutstanding) {
        profiles.save(new EquityProfileEntity(UUID.randomUUID(), instrumentId, "CTCP Test", "Test Corp",
                null, sharesOutstanding, new BigDecimal("0.800000"), "LISTED",
                LocalDate.of(2020, 1, 1), null, "FINVERA_FIXTURE", "1", null));
    }

    private void seedDailyBar(String symbol, String source, LocalDate tradingDate, BigDecimal close) {
        var result = ingestion.ingestDailyBar(new IncomingDailyBar(source, symbol, tradingDate,
                tradingDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
                close.subtract(new BigDecimal("500.000000")), close.add(new BigDecimal("500.000000")),
                close.subtract(new BigDecimal("600.000000")), close, 1_000_000L, null, "RAW", false));
        if (result.status() != IngestionStatus.ACCEPTED) {
            throw new IllegalStateException("Seed bar rejected: " + result.reasonCode());
        }
    }

    private void seedFourQuartersOfFundamentals(String symbol, int fiscalYear, BigDecimal epsPerQuarter) {
        for (int q = 1; q <= 4; q++) {
            int startMonth = (q - 1) * 3 + 1;
            LocalDate periodStart = LocalDate.of(fiscalYear, startMonth, 1);
            LocalDate periodEnd = periodStart.plusMonths(3).minusDays(1);
            List<MetricValue> metrics = List.of(
                    new MetricValue("REVENUE", new BigDecimal("10000000000.000000"), "DEFINED", null),
                    new MetricValue("NET_PROFIT", new BigDecimal("2000000000.000000"), "DEFINED", null),
                    new MetricValue("EPS", epsPerQuarter, "DEFINED", null),
                    new MetricValue("EQUITY_ATTRIBUTABLE_TO_PARENT", new BigDecimal("50000000000.000000"), "DEFINED", null),
                    new MetricValue("TOTAL_DEBT", new BigDecimal("15000000000.000000"), "DEFINED", null),
                    new MetricValue("CASH_AND_EQUIVALENTS", new BigDecimal("8000000000.000000"), "DEFINED", null),
                    new MetricValue("EBITDA", new BigDecimal("3000000000.000000"), "DEFINED", null));
            var incoming = new IncomingFundamentalReport("FINVERA_FIXTURE", symbol, "QUARTER", fiscalYear, q,
                    periodStart, periodEnd, "CONSOLIDATED", "REVIEWED", "VND", 1, "fundamental-metric-catalog-v1",
                    periodEnd.plusDays(20).atStartOfDay(ZoneOffset.UTC).toInstant(), metrics, false, null);
            var result = ingestion.ingestFundamentalReport(incoming);
            if (result.status() != IngestionStatus.ACCEPTED) {
                throw new IllegalStateException("Seed report rejected: " + result.reasonCode());
            }
        }
    }

    private UUID saveInstrument(String symbol) {
        UUID id = UUID.randomUUID();
        instruments.save(new MarketInstrumentEntity(id, null, MarketTypes.Venue.HOSE.name(), symbol, "EQUITY",
                LocalDate.of(2010, 1, 1), null, "ACTIVE", "FINVERA_FIXTURE", "v1"));
        return id;
    }
}
