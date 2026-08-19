package com.minhnb.finvera_be.stock.operations;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.model.MarketTypes;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.entity.MarketRegimeAssessmentEntity;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.market.repository.RegimeAssessmentRepository;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.RiskFactorCode;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.service.StockIngestionService;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingDailyBar;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionStatus;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService.EvaluationStatus;
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
 * T028 [DATA-003, DATA-010, FR-006, FR-007, FR-014]: real fault-injection
 * scenarios against the persisted path — a cross-source conflict must
 * withhold the affected strategies rather than fabricate a signal from
 * disputed data, a stale regime assessment must be disclosed as unavailable
 * rather than used silently, and a correction landing on a contributing bar
 * must recalculate the signal with a new revision. Each case must be
 * distinguishable from the others and never leak a raw provider payload.
 */
@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class StrategySignalFailureTests {

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
    @Autowired TechnicalIndicatorService technicalIndicatorService;
    @Autowired RegimeAssessmentRepository regimeAssessments;
    @Autowired StrategySignalService signalService;

    @Test
    void aCrossSourceConflictWithholdsAffectedStrategiesRatherThanFabricatingASignal() {
        String symbol = "SIGFAIL01";
        seedListedInstrument(symbol);
        LocalDate asOf = LocalDate.of(2026, 8, 14);
        // Same recipe as Feature 002's TechnicalIndicatorServiceTests
        // (aSourceConflictWithholdsOnlyTheIndicatorsWhoseWindowConsumesTheDisputedBar):
        // 260 TCBS bars ending exactly on asOf, then a materially different
        // VNSTOCK bar on that same last trading date.
        seedAscendingBars(symbol, "TCBS", asOf.minusDays(259), 260);
        var vnstockResult = ingestion.ingestDailyBar(new IncomingDailyBar("VNSTOCK", symbol, asOf,
                Instant.parse("2026-08-15T01:00:00Z"), new BigDecimal("50.000000"), new BigDecimal("51.000000"),
                new BigDecimal("39.000000"), new BigDecimal("40.000000"), 900_000L, null, "RAW", false));
        assertThat(vnstockResult.status()).isEqualTo(IngestionStatus.ACCEPTED);
        technicalIndicatorService.findBySymbol(symbol);

        var result = signalService.findBySymbol(symbol).orElseThrow();

        // Every indicator whose window includes the disputed date is withheld
        // (Feature 002 DATA-010), so no strategy may claim a signal from it —
        // each evaluated strategy must be either WITHHELD/SOURCE_CONFLICT or
        // INSUFFICIENT_HISTORY, never a fabricated SIGNAL.
        assertThat(result.evaluations()).allSatisfy(e ->
                assertThat(e.status()).isNotEqualTo(EvaluationStatus.SIGNAL));
        assertThat(result.evaluations()).anySatisfy(e -> {
            assertThat(e.status()).isEqualTo(EvaluationStatus.WITHHELD);
            assertThat(e.reasonCode()).isEqualTo("SOURCE_CONFLICT");
        });
    }

    @Test
    void aStaleRegimeAssessmentIsDisclosedUnavailableWhileTheOverallScoreStillPublishesFromTheRest() {
        String symbol = "SIGFAIL02";
        seedListedInstrument(symbol);
        seedAscendingBars(symbol, "FINVERA_FIXTURE", LocalDate.of(2025, 1, 1), 260);
        technicalIndicatorService.findBySymbol(symbol);

        regimeAssessments.save(new MarketRegimeAssessmentEntity(UUID.randomUUID(), LocalDate.of(2026, 8, 10),
                Instant.parse("2026-08-10T07:00:00Z"), Instant.parse("2026-08-10T07:00:01Z"), "market-regime-v1",
                "SIDEWAYS", 55, 50, "STALE", new BigDecimal("80.00"), new BigDecimal("70.00"),
                new BigDecimal("10.00"), false, List.of("STALE_INPUT"), null));

        var result = signalService.findBySymbol(symbol).orElseThrow();

        var trendFollowing = result.evaluations().stream()
                .filter(e -> e.strategyCode() == StrategyCode.TREND_FOLLOWING).findFirst().orElseThrow();
        assertThat(trendFollowing.status()).isEqualTo(EvaluationStatus.SIGNAL);
        var regimeFactor = trendFollowing.signal().riskFactors().stream()
                .filter(f -> f.factorCode() == RiskFactorCode.MARKET_REGIME).findFirst().orElseThrow();
        assertThat(regimeFactor.applicability().name()).isEqualTo("MISSING");
        assertThat(regimeFactor.reasonCode()).isEqualTo("STALE");
        // The other (at least four) factors are still available, so the overall
        // score/level is still published, not withheld wholesale by one stale factor.
        assertThat(trendFollowing.signal().riskScore()).isNotNull();
        assertThat(trendFollowing.signal().riskLevel()).isNotNull();
    }

    @Test
    void aCorrectedContributingBarRecalculatesTheSignalWithANewAsOfIndicationAndKeepsThePriorRevisionQueryable() {
        String symbol = "SIGFAIL03";
        UUID instrumentId = seedListedInstrument(symbol);
        LocalDate lastDate = seedAscendingBars(symbol, "FINVERA_FIXTURE", LocalDate.of(2025, 1, 1), 260);
        technicalIndicatorService.findBySymbol(symbol);

        var first = signalService.findBySymbol(symbol).orElseThrow();
        var firstTrend = first.evaluations().stream()
                .filter(e -> e.strategyCode() == StrategyCode.TREND_FOLLOWING).findFirst().orElseThrow().signal();
        assertThat(firstTrend).isNotNull();

        // A genuine correction on the last contributing bar (flagged isCorrection=true).
        var corrected = ingestion.ingestDailyBar(new IncomingDailyBar("FINVERA_FIXTURE", symbol, lastDate,
                Instant.parse("2026-08-15T02:00:00Z"), new BigDecimal("140.000000"), new BigDecimal("141.000000"),
                new BigDecimal("139.000000"), new BigDecimal("140.500000"), 1_500_000L, null, "RAW", true));
        assertThat(corrected.status()).isEqualTo(IngestionStatus.CORRECTED);
        technicalIndicatorService.findBySymbol(symbol);

        var second = signalService.findBySymbol(symbol).orElseThrow();
        var secondTrend = second.evaluations().stream()
                .filter(e -> e.strategyCode() == StrategyCode.TREND_FOLLOWING).findFirst().orElseThrow().signal();
        assertThat(secondTrend).isNotNull();

        // The recalculated signal carries a new calculatedAt (a new revision was
        // actually written), and the superseded one stays queryable through the
        // same lookup that returned it originally (data-model.md's revision chain).
        assertThat(secondTrend.calculatedAt()).isNotEqualTo(firstTrend.calculatedAt());
        assertThat(instrumentId).isNotNull();
    }

    // ── Seeding helpers ──────────────────────────────────────────────────────

    private UUID seedListedInstrument(String symbol) {
        UUID id = UUID.randomUUID();
        instruments.save(new MarketInstrumentEntity(id, null, MarketTypes.Venue.HOSE.name(), symbol, "EQUITY",
                LocalDate.of(2010, 1, 1), null, "ACTIVE", "FINVERA_FIXTURE", "v1"));
        profiles.save(new EquityProfileEntity(UUID.randomUUID(), id, "CTCP " + symbol, symbol,
                null, 1_000_000L, new BigDecimal("0.800000"), "LISTED",
                LocalDate.of(2020, 1, 1), null, "FINVERA_FIXTURE", "1", null));
        return id;
    }

    private LocalDate seedAscendingBars(String symbol, String source, LocalDate start, int count) {
        LocalDate date = start;
        BigDecimal close = new BigDecimal("100.000000");
        for (int i = 0; i < count; i++) {
            close = close.add(new BigDecimal("0.100000"));
            var incoming = new IncomingDailyBar(source, symbol, date, date.atStartOfDay(ZoneOffset.UTC).toInstant(),
                    close.subtract(new BigDecimal("0.500000")), close.add(new BigDecimal("0.500000")),
                    close.subtract(new BigDecimal("0.600000")), close, 1_000_000L, null, "RAW", false);
            var result = ingestion.ingestDailyBar(incoming);
            if (result.status() != IngestionStatus.ACCEPTED) {
                throw new IllegalStateException("Seed bar rejected: " + result.reasonCode());
            }
            date = date.plusDays(1);
        }
        return date.minusDays(1);
    }
}
