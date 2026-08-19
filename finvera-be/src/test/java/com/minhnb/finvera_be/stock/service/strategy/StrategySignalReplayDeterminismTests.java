package com.minhnb.finvera_be.stock.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.model.MarketTypes;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.service.StockIngestionService;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingDailyBar;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionStatus;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * T029 [FR-008, DATA-001, U-6]: identical rule version and unchanged
 * accepted inputs must reproduce an identical signal — direction, levels,
 * risk score/level, and coherence key — through the real, persisted
 * {@link StrategySignalService} path (not only the pure {@code
 * StrategySignalV1} engine, already covered by {@code StrategySignalV1Tests}),
 * and identically for a universe scan through {@link StrategyScanService}.
 */
@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class StrategySignalReplayDeterminismTests {

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
    @Autowired StrategySignalService signalService;
    @Autowired StrategyScanService scanService;

    @Test
    void identicalSignalViewReadsAgainstUnchangedAcceptedDataReproduceAnIdenticalResult() {
        String symbol = "RPLSIG01";
        seedListedInstrument(symbol);
        seedAscendingBars(symbol, LocalDate.of(2025, 1, 1), 260);
        technicalIndicatorService.findBySymbol(symbol);

        var first = signalService.findBySymbol(symbol).orElseThrow();
        var second = signalService.findBySymbol(symbol).orElseThrow();

        assertThat(second.coherenceKey()).isEqualTo(first.coherenceKey());
        var firstTrend = first.evaluations().stream()
                .filter(e -> e.strategyCode() == StrategyCode.TREND_FOLLOWING).findFirst().orElseThrow().signal();
        var secondTrend = second.evaluations().stream()
                .filter(e -> e.strategyCode() == StrategyCode.TREND_FOLLOWING).findFirst().orElseThrow().signal();
        assertThat(secondTrend.levels()).isEqualTo(firstTrend.levels());
        assertThat(secondTrend.direction()).isEqualTo(firstTrend.direction());
        assertThat(secondTrend.riskScore()).isEqualTo(firstTrend.riskScore());
        assertThat(secondTrend.riskLevel()).isEqualTo(firstTrend.riskLevel());
        // Compared at millisecond precision: a timestamptz value round-tripped
        // through Postgres can differ from the in-memory Instant by a
        // sub-microsecond rounding artifact (see StrategySignalServiceTests).
        assertThat(secondTrend.calculatedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
                .isEqualTo(firstTrend.calculatedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    }

    @Test
    void identicalStrategyScansAgainstUnchangedAcceptedDataReproduceTheSameMatchSet() {
        seedListedInstrument("RPLSCAN01");
        seedAscendingBars("RPLSCAN01", LocalDate.of(2025, 1, 1), 260);
        technicalIndicatorService.findBySymbol("RPLSCAN01");

        var first = scanService.scan(StrategyCode.TREND_FOLLOWING, 50, 0);
        var second = scanService.scan(StrategyCode.TREND_FOLLOWING, 50, 0);

        assertThat(second.totalMatchCount()).isEqualTo(first.totalMatchCount());
        assertThat(second.matches().stream().map(m -> m.symbol()).toList())
                .isEqualTo(first.matches().stream().map(m -> m.symbol()).toList());
        assertThat(first.matches()).anySatisfy(m -> assertThat(m.symbol()).isEqualTo("RPLSCAN01"));
    }

    private void seedListedInstrument(String symbol) {
        UUID id = UUID.randomUUID();
        instruments.save(new MarketInstrumentEntity(id, null, MarketTypes.Venue.HOSE.name(), symbol, "EQUITY",
                LocalDate.of(2010, 1, 1), null, "ACTIVE", "FINVERA_FIXTURE", "v1"));
        profiles.save(new EquityProfileEntity(UUID.randomUUID(), id, "CTCP " + symbol, symbol,
                null, 1_000_000L, new BigDecimal("0.800000"), "LISTED",
                LocalDate.of(2020, 1, 1), null, "FINVERA_FIXTURE", "1", null));
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
