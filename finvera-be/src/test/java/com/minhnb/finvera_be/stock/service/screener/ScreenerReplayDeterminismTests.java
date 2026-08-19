package com.minhnb.finvera_be.stock.service.screener;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.model.MarketTypes;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.FundamentalFilter;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.MarketFilter;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.PriceFilter;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.ScreenCriteria;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.TechnicalFilter;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.service.FundamentalReportService;
import com.minhnb.finvera_be.stock.service.StockIngestionService;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingDailyBar;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingFundamentalReport;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionStatus;
import com.minhnb.finvera_be.stock.service.StockIngestionService.MetricValue;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService.SortDirection;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService.SortField;
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
 * T029 [FR-009, FR-010, DATA-004, SC-003]: identical filters against
 * unchanged accepted inputs must reproduce an identical match set, identical
 * matched values, and identical coherence key (S-5,
 * {@code contracts/screener-v1.md}). Exercises the full four-category
 * screen through the real Postgres-backed {@link ScreenerService}, not only
 * the pure {@code ScreenerV1} engine (already covered by
 * {@code ScreenerV1Tests}) — this is the persisted-and-refetched replay
 * guarantee, matching the distinction {@code StockReplayDeterminismTests}
 * (Feature 002) already draws between in-memory and persisted replay.
 */
@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class ScreenerReplayDeterminismTests {

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
    @Autowired FundamentalReportService fundamentalReportService;
    @Autowired ScreenerService screener;

    @Test
    void identicalFourCategoryScreensAgainstUnchangedAcceptedDataReproduceIdenticalResults() {
        String symbol = "RPL01";
        UUID instrumentId = UUID.randomUUID();
        instruments.save(new MarketInstrumentEntity(instrumentId, null, MarketTypes.Venue.HOSE.name(), symbol,
                "EQUITY", LocalDate.of(2010, 1, 1), null, "ACTIVE", "FINVERA_FIXTURE", "v1"));
        profiles.save(new EquityProfileEntity(UUID.randomUUID(), instrumentId, "CTCP " + symbol, symbol,
                null, 1_000_000L, new BigDecimal("0.800000"), "LISTED",
                LocalDate.of(2020, 1, 1), null, "FINVERA_FIXTURE", "1", null));

        LocalDate start = LocalDate.of(2025, 1, 1);
        BigDecimal close = new BigDecimal("100.000000");
        for (int i = 0; i < 260; i++) {
            close = close.add(new BigDecimal("0.100000"));
            LocalDate date = start.plusDays(i);
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

        for (int q = 1; q <= 4; q++) {
            int startMonth = (q - 1) * 3 + 1;
            LocalDate periodStart = LocalDate.of(2026, startMonth, 1);
            LocalDate periodEnd = periodStart.plusMonths(3).minusDays(1);
            List<MetricValue> metrics = List.of(
                    new MetricValue("REVENUE", new BigDecimal("10000000000.000000"), "DEFINED", null),
                    new MetricValue("ROE", new BigDecimal("15.500000"), "DEFINED", null));
            var incoming = new IncomingFundamentalReport("FINVERA_FIXTURE", symbol, "QUARTER", 2026, q,
                    periodStart, periodEnd, "CONSOLIDATED", "REVIEWED", "VND", 1, "fundamental-metric-catalog-v1",
                    periodEnd.plusDays(20).atStartOfDay(ZoneOffset.UTC).toInstant(), metrics, false, null);
            var result = ingestion.ingestFundamentalReport(incoming);
            if (result.status() != IngestionStatus.ACCEPTED) {
                throw new IllegalStateException("Seed report rejected: " + result.reasonCode());
            }
        }
        fundamentalReportService.findBySymbol(symbol);

        var criteria = new ScreenCriteria(
                new MarketFilter(java.util.Set.of("HOSE"), null, null, null),
                new PriceFilter(BigDecimal.ONE, new BigDecimal("999999"), null, null),
                new TechnicalFilter(BigDecimal.ZERO, new BigDecimal("100"), null, null, null, null, null, null,
                        null, null),
                new FundamentalFilter(null, null, null, null, new BigDecimal("10"), new BigDecimal("20"), null,
                        null, null, null, null, null, null, null));

        var first = screener.execute(criteria, SortField.SYMBOL, SortDirection.ASC, 50, 0);
        var second = screener.execute(criteria, SortField.SYMBOL, SortDirection.ASC, 50, 0);

        assertThat(second.matches()).hasSameSizeAs(first.matches());
        assertThat(second.matches().stream().map(m -> m.symbol()).toList())
                .isEqualTo(first.matches().stream().map(m -> m.symbol()).toList());
        assertThat(second.matches().get(0).matchedValues()).isEqualTo(first.matches().get(0).matchedValues());
        assertThat(second.coherenceKey()).isEqualTo(first.coherenceKey());
        assertThat(first.matches()).anySatisfy(m -> assertThat(m.symbol()).isEqualTo(symbol));
    }
}
