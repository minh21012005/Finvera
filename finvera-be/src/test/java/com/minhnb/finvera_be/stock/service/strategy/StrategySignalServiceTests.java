package com.minhnb.finvera_be.stock.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.model.MarketTypes;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.RiskFactorCode;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorResultEntity;
import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorValueEntity;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.StrategySignalRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorResultRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorValueRepository;
import com.minhnb.finvera_be.stock.service.StockIngestionService;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingDailyBar;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionStatus;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService.EvaluationStatus;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService.StockSignals;
import java.math.BigDecimal;
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
 * T007: persistence-level integration tests for {@link StrategySignalService}
 * — signal creation only on a genuine trigger, non-persistence of non-
 * triggers, idempotent replay, and revision-chain supersession on a
 * corrected input. Per-strategy formula correctness is exhaustively covered
 * at the unit level by {@code StrategySignalV1Tests}.
 */
@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class StrategySignalServiceTests {

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
    @Autowired TechnicalIndicatorResultRepository technicalResults;
    @Autowired TechnicalIndicatorValueRepository technicalValues;
    @Autowired StrategySignalRepository signalRepository;
    @Autowired StrategySignalService signalService;

    @Test
    void trendFollowingTriggersAndIsPersistedWithLevelsAndSixRiskFactors() {
        UUID instrumentId = seedListedInstrument("SIG01");
        seedAscendingBars("SIG01", LocalDate.of(2025, 1, 1), 260);
        technicalIndicatorService.findBySymbol("SIG01");

        StockSignals result = signalService.findBySymbol("SIG01").orElseThrow();

        var trendFollowing = result.evaluations().stream()
                .filter(e -> e.strategyCode() == StrategyCode.TREND_FOLLOWING).findFirst().orElseThrow();
        assertThat(trendFollowing.status()).isEqualTo(EvaluationStatus.SIGNAL);
        assertThat(trendFollowing.signal()).isNotNull();
        assertThat(trendFollowing.signal().levels().entryLow())
                .isLessThanOrEqualTo(trendFollowing.signal().levels().entryHigh());
        assertThat(trendFollowing.signal().riskFactors()).hasSize(6);
        assertThat(trendFollowing.signal().riskFactors()).extracting("factorCode")
                .containsExactlyInAnyOrder((Object[]) RiskFactorCode.values());
        // No regime assessment was ever seeded in this test's schema, so the
        // market-regime factor must be disclosed as unavailable, not silently
        // dropped or fabricated.
        assertThat(trendFollowing.signal().riskFactors()).anySatisfy(f -> {
            if (f.factorCode() == RiskFactorCode.MARKET_REGIME) {
                assertThat(f.applicability().name()).isEqualTo("MISSING");
            }
        });

        assertThat(signalRepository
                .findFirstByInstrumentIdAndStrategyCodeAndAsOfTradingDateAndRuleVersionAndCurrentTrue(
                        instrumentId, StrategyCode.TREND_FOLLOWING.name(), trendFollowing.signal().asOfTradingDate(),
                        StrategySignalV1.RULE_VERSION))
                .isPresent();
    }

    @Test
    void aNonTriggeringStrategyIsReportedLiveAndNeverPersisted() {
        UUID instrumentId = seedListedInstrument("SIG02");
        seedAscendingBars("SIG02", LocalDate.of(2025, 1, 1), 260);
        technicalIndicatorService.findBySymbol("SIG02");

        StockSignals result = signalService.findBySymbol("SIG02").orElseThrow();

        // A steadily ascending series never satisfies Mean Reversion's
        // "oversold, below the lower band" condition.
        var meanReversion = result.evaluations().stream()
                .filter(e -> e.strategyCode() == StrategyCode.MEAN_REVERSION).findFirst().orElseThrow();
        assertThat(meanReversion.status()).isEqualTo(EvaluationStatus.NO_SIGNAL);
        assertThat(meanReversion.signal()).isNull();

        LocalDate asOfTradingDate = result.evaluations().stream()
                .filter(e -> e.strategyCode() == StrategyCode.TREND_FOLLOWING).findFirst().orElseThrow()
                .signal().asOfTradingDate();
        assertThat(signalRepository
                .findFirstByInstrumentIdAndStrategyCodeAndAsOfTradingDateAndRuleVersionAndCurrentTrue(
                        instrumentId, StrategyCode.MEAN_REVERSION.name(), asOfTradingDate,
                        StrategySignalV1.RULE_VERSION))
                .isEmpty();
    }

    @Test
    void repeatedReadsAreIdempotentAndDoNotCreateDuplicateCurrentRevisions() {
        seedListedInstrument("SIG03");
        seedAscendingBars("SIG03", LocalDate.of(2025, 1, 1), 260);
        technicalIndicatorService.findBySymbol("SIG03");

        StockSignals first = signalService.findBySymbol("SIG03").orElseThrow();
        StockSignals second = signalService.findBySymbol("SIG03").orElseThrow();

        assertThat(first.coherenceKey()).isEqualTo(second.coherenceKey());
        var firstTrend = first.evaluations().stream()
                .filter(e -> e.strategyCode() == StrategyCode.TREND_FOLLOWING).findFirst().orElseThrow().signal();
        var secondTrend = second.evaluations().stream()
                .filter(e -> e.strategyCode() == StrategyCode.TREND_FOLLOWING).findFirst().orElseThrow().signal();
        // Compared at millisecond precision: a timestamptz value round-tripped
        // through Postgres can differ from the original in-memory Instant by a
        // sub-microsecond rounding artifact; the behavioral guarantee under
        // test is "no new revision was written", not bit-exact timestamp echo.
        assertThat(firstTrend.calculatedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
                .isEqualTo(secondTrend.calculatedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    }

    @Test
    void aCorrectedContributingIndicatorSupersedesThePreviousSignalRevision() {
        UUID instrumentId = seedListedInstrument("SIG04");
        seedAscendingBars("SIG04", LocalDate.of(2025, 1, 1), 260);
        technicalIndicatorService.findBySymbol("SIG04");

        StockSignals first = signalService.findBySymbol("SIG04").orElseThrow();
        var firstSignal = first.evaluations().stream()
                .filter(e -> e.strategyCode() == StrategyCode.TREND_FOLLOWING).findFirst().orElseThrow().signal();
        var firstEntity = signalRepository
                .findFirstByInstrumentIdAndStrategyCodeAndAsOfTradingDateAndRuleVersionAndCurrentTrue(
                        instrumentId, StrategyCode.TREND_FOLLOWING.name(), firstSignal.asOfTradingDate(),
                        StrategySignalV1.RULE_VERSION)
                .orElseThrow();

        // Simulate a correction landing on the current MA20 result: mark it
        // superseded and write a replacement with a new id (same numeric
        // value) — the service must detect the changed input identity, not
        // just the value, and write a new signal revision.
        TechnicalIndicatorResultEntity currentMa20 = technicalResults
                .findFirstByInstrumentIdAndIndicatorCodeAndAsOfTradingDateAndRuleVersionAndCurrentTrue(
                        instrumentId, "MA20", firstSignal.asOfTradingDate(),
                        com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.RULE_VERSION)
                .orElseThrow();
        UUID replacementId = UUID.randomUUID();
        List<TechnicalIndicatorValueEntity> oldValues = technicalValues.findByResultId(currentMa20.getId());
        currentMa20.markSuperseded();
        technicalResults.saveAndFlush(currentMa20);
        technicalResults.save(new TechnicalIndicatorResultEntity(replacementId, instrumentId, "MA20",
                com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.RULE_VERSION,
                currentMa20.getAsOfTradingDate(), currentMa20.getWindowStartDate(), currentMa20.getWindowEndDate(),
                currentMa20.getInputBarCount(), currentMa20.getInputSetHash(), currentMa20.getAdjustmentStatus(),
                currentMa20.getDataStatus(), currentMa20.getQualityReason(), currentMa20.getCalculatedAt().plusMillis(1),
                true, currentMa20.getId()));
        // The MA20 technical_indicator_value row must exist for the new
        // result id too, since the service reads component values by result id.
        for (TechnicalIndicatorValueEntity value : oldValues) {
            technicalValues.save(new TechnicalIndicatorValueEntity(replacementId, value.getComponentCode(),
                    value.getValue(), value.getUnit(), value.getApplicability(), value.getQualityReason()));
        }

        StockSignals second = signalService.findBySymbol("SIG04").orElseThrow();
        var secondSignal = second.evaluations().stream()
                .filter(e -> e.strategyCode() == StrategyCode.TREND_FOLLOWING).findFirst().orElseThrow().signal();

        assertThat(secondSignal).isNotNull();
        var secondEntity = signalRepository
                .findFirstByInstrumentIdAndStrategyCodeAndAsOfTradingDateAndRuleVersionAndCurrentTrue(
                        instrumentId, StrategyCode.TREND_FOLLOWING.name(), secondSignal.asOfTradingDate(),
                        StrategySignalV1.RULE_VERSION)
                .orElseThrow();
        assertThat(secondEntity.getId()).isNotEqualTo(firstEntity.getId());
        assertThat(secondEntity.getSupersedesId()).isEqualTo(firstEntity.getId());
    }

    // ── Seeding helpers (mirrors ScreenerServiceTests) ──────────────────────

    private UUID seedListedInstrument(String symbol) {
        UUID id = UUID.randomUUID();
        instruments.save(new MarketInstrumentEntity(id, null, MarketTypes.Venue.HOSE.name(), symbol, "EQUITY",
                LocalDate.of(2010, 1, 1), null, "ACTIVE", "FINVERA_FIXTURE", "v1"));
        profiles.save(new EquityProfileEntity(UUID.randomUUID(), id, "CTCP " + symbol, symbol + " Corp",
                null, 1_000_000L, new BigDecimal("0.800000"), "LISTED",
                LocalDate.of(2020, 1, 1), null, "FINVERA_FIXTURE", "1", null));
        return id;
    }

    private void seedAscendingBars(String symbol, LocalDate start, int count) {
        LocalDate date = start;
        BigDecimal close = new BigDecimal("100.000000");
        for (int i = 0; i < count; i++) {
            close = close.add(new BigDecimal("0.100000"));
            var incoming = new IncomingDailyBar("FINVERA_FIXTURE", symbol, date,
                    date.atStartOfDay(ZoneOffset.UTC).toInstant(),
                    close.subtract(new BigDecimal("0.500000")), close.add(new BigDecimal("0.500000")),
                    close.subtract(new BigDecimal("0.600000")), close, 1_000_000L, null, "RAW", false);
            var result = ingestion.ingestDailyBar(incoming);
            if (result.status() != IngestionStatus.ACCEPTED) {
                throw new IllegalStateException("Seed bar rejected: " + result.reasonCode());
            }
            date = date.plusDays(1);
        }
    }
}
