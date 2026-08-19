package com.minhnb.finvera_be.stock.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.model.MarketTypes;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.MarketFilter;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.PriceFilter;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.ScreenCriteria;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.TechnicalFilter;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.service.StockIngestionService;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingDailyBar;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionStatus;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService.SortDirection;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService.SortField;
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
 * T027 [NFR-001, SC-002]: 95% of screen executions over the supported
 * universe must return within 5 seconds (SRS 36.1). Seeds a representative
 * multi-instrument universe (Market/Price data for every instrument, full
 * 250-bar Technical history for a subset), then measures p95 latency across
 * 20 multi-category screens.
 */
@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class ScreenerPerformanceTests {

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
    private static final int TECHNICAL_COVERAGE_COUNT = 20;
    private static final int RUNS = 20;

    @Autowired MarketInstrumentRepository instruments;
    @Autowired EquityProfileRepository profiles;
    @Autowired StockIngestionService ingestion;
    @Autowired TechnicalIndicatorService technicalIndicatorService;
    @Autowired ScreenerService screener;

    @Test
    void p95ScreenLatencyStaysWithinTheFiveSecondBaseline() {
        seedRepresentativeUniverse();

        var criteria = new ScreenCriteria(
                new MarketFilter(java.util.Set.of("HOSE"), null, null, null),
                new PriceFilter(BigDecimal.ONE, new BigDecimal("999999999"), null, null),
                new TechnicalFilter(BigDecimal.ZERO, new BigDecimal("100"), null, null, null, null, null, null,
                        null, null),
                null);

        List<Long> elapsedMillis = new ArrayList<>();
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            var result = screener.execute(criteria, SortField.SYMBOL, SortDirection.ASC, 200, 0);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            elapsedMillis.add(elapsed);
            assertThat(result).isNotNull();
        }

        elapsedMillis.sort(Long::compareTo);
        int p95Index = (int) Math.ceil(0.95 * elapsedMillis.size()) - 1;
        long p95 = elapsedMillis.get(Math.max(p95Index, 0));

        assertThat(p95)
                .as("p95 latency over %d representative multi-category screens, individual runs=%s",
                        RUNS, elapsedMillis)
                .isLessThanOrEqualTo(5000L);
    }

    private void seedRepresentativeUniverse() {
        for (int i = 0; i < UNIVERSE_SIZE; i++) {
            String symbol = "PERF" + String.format("%03d", i);
            UUID instrumentId = UUID.randomUUID();
            instruments.save(new MarketInstrumentEntity(instrumentId, null, MarketTypes.Venue.HOSE.name(), symbol,
                    "EQUITY", LocalDate.of(2010, 1, 1), null, "ACTIVE", "FINVERA_FIXTURE", "v1"));
            profiles.save(new EquityProfileEntity(UUID.randomUUID(), instrumentId, "CTCP " + symbol, symbol,
                    null, 1_000_000L, new BigDecimal("0.800000"), "LISTED",
                    LocalDate.of(2020, 1, 1), null, "FINVERA_FIXTURE", "1", null));

            if (i < TECHNICAL_COVERAGE_COUNT) {
                LocalDate start = LocalDate.of(2025, 1, 1);
                BigDecimal close = new BigDecimal("100.000000");
                for (int d = 0; d < 260; d++) {
                    close = close.add(new BigDecimal("0.050000"));
                    LocalDate date = start.plusDays(d);
                    var incoming = new IncomingDailyBar("FINVERA_FIXTURE", symbol, date,
                            date.atStartOfDay(ZoneOffset.UTC).toInstant(),
                            close.subtract(new BigDecimal("0.500000")), close.add(new BigDecimal("0.500000")),
                            close.subtract(new BigDecimal("0.600000")), close, 1_000_000L, null, "RAW", false);
                    var result = ingestion.ingestDailyBar(incoming);
                    if (result.status() != IngestionStatus.ACCEPTED) {
                        throw new IllegalStateException("Seed bar rejected: " + result.reasonCode());
                    }
                }
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
}
