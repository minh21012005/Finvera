package com.minhnb.finvera_be.stock.performance;

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
import com.minhnb.finvera_be.stock.service.strategy.StrategyScanService;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
 * T027 [NFR-001, NFR-002]: same 5-second p95 baseline Feature 003's own
 * spec.md's own two baselines: NFR-001 gives the single-stock signal view 3
 * seconds (Feature 002's primary-read-view baseline); NFR-002 gives the
 * universe-wide strategy scan 5 seconds, matching {@code
 * ScreenerPerformanceTests}'s own baseline for the comparable bulk-fetch
 * operation (research R-007 reuses that execution strategy directly).
 */
@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class StrategySignalPerformanceTests {

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

    private static final int UNIVERSE_SIZE = 60;
    private static final int RUNS = 20;
    private static final long SIGNAL_VIEW_BASELINE_MILLIS = 3000L;
    private static final long SCAN_BASELINE_MILLIS = 5000L;

    @Autowired MarketInstrumentRepository instruments;
    @Autowired EquityProfileRepository profiles;
    @Autowired StockIngestionService ingestion;
    @Autowired TechnicalIndicatorService technicalIndicatorService;
    @Autowired StrategySignalService signalService;
    @Autowired StrategyScanService scanService;

    @Test
    void p95SingleStockSignalViewLatencyStaysWithinTheThreeSecondBaseline() {
        String symbol = seedOneFullHistoryInstrument("SIGPERF01");

        List<Long> elapsedMillis = new ArrayList<>();
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            var result = signalService.findBySymbol(symbol);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            elapsedMillis.add(elapsed);
            assertThat(result).isPresent();
        }

        assertP95WithinBaseline(elapsedMillis, "single-stock signal view", SIGNAL_VIEW_BASELINE_MILLIS);
    }

    @Test
    void p95StrategyScanLatencyStaysWithinTheFiveSecondBaseline() {
        seedRepresentativeUniverse();

        List<Long> elapsedMillis = new ArrayList<>();
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            var result = scanService.scan(StrategyCode.TREND_FOLLOWING, 200, 0);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            elapsedMillis.add(elapsed);
            assertThat(result).isNotNull();
        }

        assertP95WithinBaseline(elapsedMillis, "strategy scan over the representative universe", SCAN_BASELINE_MILLIS);
    }

    private static void assertP95WithinBaseline(List<Long> elapsedMillis, String label, long baselineMillis) {
        List<Long> sorted = new ArrayList<>(elapsedMillis);
        sorted.sort(Long::compareTo);
        int p95Index = (int) Math.ceil(0.95 * sorted.size()) - 1;
        long p95 = sorted.get(Math.max(p95Index, 0));
        assertThat(p95)
                .as("p95 latency over %d runs of %s, individual runs=%s", RUNS, label, elapsedMillis)
                .isLessThanOrEqualTo(baselineMillis);
    }

    private String seedOneFullHistoryInstrument(String symbol) {
        UUID instrumentId = UUID.randomUUID();
        instruments.save(new MarketInstrumentEntity(instrumentId, null, MarketTypes.Venue.HOSE.name(), symbol,
                "EQUITY", LocalDate.of(2010, 1, 1), null, "ACTIVE", "FINVERA_FIXTURE", "v1"));
        profiles.save(new EquityProfileEntity(UUID.randomUUID(), instrumentId, "CTCP " + symbol, symbol,
                null, 1_000_000L, new BigDecimal("0.800000"), "LISTED",
                LocalDate.of(2020, 1, 1), null, "FINVERA_FIXTURE", "1", null));
        seedAscendingBars(symbol, LocalDate.of(2025, 1, 1), 260);
        technicalIndicatorService.findBySymbol(symbol);
        return symbol;
    }

    private void seedRepresentativeUniverse() {
        for (int i = 0; i < UNIVERSE_SIZE; i++) {
            String symbol = "SCANPERF" + String.format("%03d", i);
            UUID instrumentId = UUID.randomUUID();
            instruments.save(new MarketInstrumentEntity(instrumentId, null, MarketTypes.Venue.HOSE.name(), symbol,
                    "EQUITY", LocalDate.of(2010, 1, 1), null, "ACTIVE", "FINVERA_FIXTURE", "v1"));
            profiles.save(new EquityProfileEntity(UUID.randomUUID(), instrumentId, "CTCP " + symbol, symbol,
                    null, 1_000_000L, new BigDecimal("0.800000"), "LISTED",
                    LocalDate.of(2020, 1, 1), null, "FINVERA_FIXTURE", "1", null));
            if (i < 20) {
                seedAscendingBars(symbol, LocalDate.of(2025, 1, 1), 260);
                technicalIndicatorService.findBySymbol(symbol);
            } else {
                var incoming = new IncomingDailyBar("FINVERA_FIXTURE", symbol, LocalDate.of(2026, 8, 14),
                        LocalDate.of(2026, 8, 14).atStartOfDay(ZoneOffset.UTC).toInstant(),
                        new BigDecimal("99.500000"), new BigDecimal("100.500000"),
                        new BigDecimal("99.400000"), new BigDecimal("100.000000"), 500_000L, null, "RAW", false);
                var result = ingestion.ingestDailyBar(incoming);
                if (result.status() != IngestionStatus.ACCEPTED) {
                    throw new IllegalStateException("Seed bar rejected: " + result.reasonCode());
                }
            }
        }
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
