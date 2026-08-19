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
import com.minhnb.finvera_be.stock.service.ValuationService;
import com.minhnb.finvera_be.stock.service.screener.ScreenerService.ScreenExecutionResult;
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
 * Feature 003 T007/T016/T021: orchestration-level integration tests for
 * {@link ScreenerService} — the two-pass fetch, category disclosure, sort,
 * pagination, and coherence-key behavior. Per-filter formula correctness is
 * exhaustively covered at the unit level by {@code ScreenerV1Tests}; these
 * tests exercise the real Postgres-backed wiring on top of it.
 */
@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class ScreenerServiceTests {

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
    @Autowired ValuationService valuationService;
    @Autowired ScreenerService screener;

    @Test
    void marketAndPriceScreenReturnsExactlyTheMatchingInstruments() {
        // Testcontainers Postgres is shared across every test method in this class
        // (no per-test reset, matching the rest of this codebase's integration-test
        // convention), so this price band uses deliberately unique values no other
        // test in this file seeds, keeping the exact-match-set assertion below
        // order-independent rather than fragile against sibling test data.
        seedListedInstrument("SCR01", MarketTypes.Venue.HOSE.name(), 1_000_000L);
        seedDailyBar("SCR01", LocalDate.of(2026, 8, 14), new BigDecimal("123456.000000"));
        seedListedInstrument("SCR02", MarketTypes.Venue.HNX.name(), 1_000_000L);
        seedDailyBar("SCR02", LocalDate.of(2026, 8, 14), new BigDecimal("123457.000000"));

        var criteria = new ScreenCriteria(
                new MarketFilter(java.util.Set.of("HOSE"), null, null, null),
                new PriceFilter(new BigDecimal("123450"), new BigDecimal("123460"), null, null),
                null, null);
        ScreenExecutionResult result = screener.execute(criteria, SortField.SYMBOL, SortDirection.ASC, 50, 0);

        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).symbol()).isEqualTo("SCR01");
        assertThat(result.matches().get(0).matchedValues()).containsEntry("price", "123456.000000");
    }

    @Test
    void emptyRequestReturnsTheFullListedCandidateUniverse() {
        seedListedInstrument("SCR03", MarketTypes.Venue.HOSE.name(), 1_000_000L);
        seedListedInstrument("SCR04", MarketTypes.Venue.HOSE.name(), 1_000_000L);

        var criteria = new ScreenCriteria(null, null, null, null);
        ScreenExecutionResult result = screener.execute(criteria, SortField.SYMBOL, SortDirection.ASC, 200, 0);

        assertThat(result.matches().stream().map(ScreenerService.ScreenMatch::symbol))
                .contains("SCR03", "SCR04");
    }

    @Test
    void technicalFilterEvaluatesPersistedIndicatorResultsNotRawBars() {
        seedListedInstrument("SCR05", MarketTypes.Venue.HOSE.name(), 1_000_000L);
        LocalDate asOf = seedAscendingBars("SCR05", LocalDate.of(2025, 1, 1), 260);
        // Trigger the technical-indicators-v1 engine to compute and persist.
        technicalIndicatorService.findBySymbol("SCR05");

        var criteria = new ScreenCriteria(null, null,
                new TechnicalFilter(BigDecimal.ZERO, new BigDecimal("100"), null, null, null, null, null, null,
                        null, null),
                null);
        ScreenExecutionResult result = screener.execute(criteria, SortField.SYMBOL, SortDirection.ASC, 50, 0);

        // The candidate universe (and therefore the TECHNICAL category disclosure)
        // is shared with every other test in this class, so this assertion checks
        // SCR05's own membership rather than the aggregate disclosure status, which
        // a sibling test's insufficient-history candidate would otherwise make PARTIAL.
        assertThat(result.matches()).anySatisfy(m -> assertThat(m.symbol()).isEqualTo("SCR05"));
        assertThat(result.categoryDisclosures()).anySatisfy(d -> assertThat(d.category()).isEqualTo("TECHNICAL"));
    }

    @Test
    void technicalFilterExcludesAnInstrumentWithFewerBarsThanTheIndicatorRequires() {
        seedListedInstrument("SCR06", MarketTypes.Venue.HOSE.name(), 1_000_000L);
        seedAscendingBars("SCR06", LocalDate.of(2026, 8, 1), 5); // far below the 20-bar RSI/MA20 minimum
        technicalIndicatorService.findBySymbol("SCR06");

        var criteria = new ScreenCriteria(null, null,
                new TechnicalFilter(BigDecimal.ZERO, new BigDecimal("100"), null, null, null, null, null, null,
                        null, null),
                null);
        ScreenExecutionResult result = screener.execute(criteria, SortField.SYMBOL, SortDirection.ASC, 50, 0);

        // RSI spans 0-100, so any instrument with a computed RSI would match; SCR06
        // never gets one, which is the S-4 exclusion this test actually proves.
        assertThat(result.matches()).noneSatisfy(m -> assertThat(m.symbol()).isEqualTo("SCR06"));
    }

    @Test
    void fundamentalFilterUsesTheRevenueGrowthPercentExtension() {
        seedListedInstrument("SCR07", MarketTypes.Venue.HOSE.name(), 1_000_000L);
        seedDailyBar("SCR07", LocalDate.of(2026, 8, 14), new BigDecimal("50000.000000"));
        seedEightQuartersOfFundamentals("SCR07");
        fundamentalReportService.findBySymbol("SCR07"); // triggers fundamental-summary-v1

        var criteria = new ScreenCriteria(null, null, null,
                new FundamentalFilter(BigDecimal.ZERO, new BigDecimal("999"), null, null, null, null, null, null,
                        null, null, null, null, null, null));
        ScreenExecutionResult result = screener.execute(criteria, SortField.SYMBOL, SortDirection.ASC, 50, 0);

        assertThat(result.matches()).anySatisfy(m -> {
            assertThat(m.symbol()).isEqualTo("SCR07");
            assertThat(m.matchedValues()).containsKey("revenueGrowthPercent");
        });
    }

    @Test
    void peFilterExcludesAnInstrumentWhoseValuationIsWithheld() {
        seedListedInstrument("SCR08", MarketTypes.Venue.HOSE.name(), 1_000_000L);
        seedDailyBar("SCR08", LocalDate.of(2026, 8, 14), new BigDecimal("50000.000000"));
        seedEightQuartersOfFundamentals("SCR08");
        var withheld = valuationService.findBySymbol("SCR08").orElseThrow();
        assertThat(withheld.published()).isFalse(); // one bar is far below the H_min=500 own-history floor

        var criteria = new ScreenCriteria(null, null, null,
                new FundamentalFilter(null, null, null, null, null, null, null, null, BigDecimal.ZERO,
                        new BigDecimal("999"), null, null, null, null));
        ScreenExecutionResult result = screener.execute(criteria, SortField.SYMBOL, SortDirection.ASC, 50, 0);

        assertThat(result.matches()).noneSatisfy(m -> assertThat(m.symbol()).isEqualTo("SCR08"));
    }

    @Test
    void contradictoryPriceRangeIsRejectedBeforeAnyQueryRuns() {
        var criteria = new ScreenCriteria(null,
                new PriceFilter(new BigDecimal("100"), new BigDecimal("50"), null, null), null, null);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> screener.execute(criteria, SortField.SYMBOL, SortDirection.ASC, 50, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void repeatedIdenticalScreensProduceAnIdenticalCoherenceKey() {
        seedListedInstrument("SCR09", MarketTypes.Venue.HOSE.name(), 1_000_000L);
        seedDailyBar("SCR09", LocalDate.of(2026, 8, 14), new BigDecimal("75000.000000"));

        var criteria = new ScreenCriteria(null,
                new PriceFilter(new BigDecimal("1"), new BigDecimal("999999"), null, null), null, null);
        var first = screener.execute(criteria, SortField.SYMBOL, SortDirection.ASC, 50, 0);
        var second = screener.execute(criteria, SortField.SYMBOL, SortDirection.ASC, 50, 0);

        assertThat(first.coherenceKey()).isEqualTo(second.coherenceKey());
    }

    @Test
    void sortingByPriceDescendingOrdersMatchesFromHighestToLowest() {
        seedListedInstrument("SCR10", MarketTypes.Venue.HOSE.name(), 1_000_000L);
        seedDailyBar("SCR10", LocalDate.of(2026, 8, 14), new BigDecimal("10000.000000"));
        seedListedInstrument("SCR11", MarketTypes.Venue.HOSE.name(), 1_000_000L);
        seedDailyBar("SCR11", LocalDate.of(2026, 8, 14), new BigDecimal("90000.000000"));

        var criteria = new ScreenCriteria(null,
                new PriceFilter(new BigDecimal("1"), new BigDecimal("999999"), null, null), null, null);
        var result = screener.execute(criteria, SortField.PRICE, SortDirection.DESC, 50, 0);

        var symbols = result.matches().stream().map(ScreenerService.ScreenMatch::symbol)
                .filter(s -> s.equals("SCR10") || s.equals("SCR11")).toList();
        assertThat(symbols).containsExactly("SCR11", "SCR10");
    }

    // ── Seeding helpers ──────────────────────────────────────────────────────

    private UUID seedListedInstrument(String symbol, String venue, long sharesOutstanding) {
        UUID id = UUID.randomUUID();
        instruments.save(new MarketInstrumentEntity(id, null, venue, symbol, "EQUITY",
                LocalDate.of(2010, 1, 1), null, "ACTIVE", "FINVERA_FIXTURE", "v1"));
        profiles.save(new EquityProfileEntity(UUID.randomUUID(), id, "CTCP " + symbol, symbol + " Corp",
                null, sharesOutstanding, new BigDecimal("0.800000"), "LISTED",
                LocalDate.of(2020, 1, 1), null, "FINVERA_FIXTURE", "1", null));
        return id;
    }

    private void seedDailyBar(String symbol, LocalDate tradingDate, BigDecimal close) {
        var result = ingestion.ingestDailyBar(new IncomingDailyBar("FINVERA_FIXTURE", symbol, tradingDate,
                tradingDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
                close.subtract(new BigDecimal("500.000000")), close.add(new BigDecimal("500.000000")),
                close.subtract(new BigDecimal("600.000000")), close, 1_000_000L, null, "RAW", false));
        if (result.status() != IngestionStatus.ACCEPTED) {
            throw new IllegalStateException("Seed bar rejected: " + result.reasonCode());
        }
    }

    /**
     * Small proportional OHLC offsets (unlike {@link #seedDailyBar}'s fixed
     * +/-500/600 VND spread, which would go negative around a close near 100).
     */
    private LocalDate seedAscendingBars(String symbol, LocalDate start, int count) {
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
        return date.minusDays(1);
    }

    private void seedEightQuartersOfFundamentals(String symbol) {
        BigDecimal revenue = new BigDecimal("10000000000.000000");
        for (int yearOffset = 0; yearOffset < 2; yearOffset++) {
            int fiscalYear = 2024 + yearOffset;
            for (int q = 1; q <= 4; q++) {
                int startMonth = (q - 1) * 3 + 1;
                LocalDate periodStart = LocalDate.of(fiscalYear, startMonth, 1);
                LocalDate periodEnd = periodStart.plusMonths(3).minusDays(1);
                BigDecimal periodRevenue = revenue.add(BigDecimal.valueOf((long) yearOffset * 4_000_000_000L));
                List<MetricValue> metrics = List.of(
                        new MetricValue("REVENUE", periodRevenue, "DEFINED", null),
                        new MetricValue("NET_PROFIT", new BigDecimal("2000000000.000000"), "DEFINED", null),
                        new MetricValue("EPS", new BigDecimal("500.000000"), "DEFINED", null),
                        new MetricValue("ROE", new BigDecimal("15.500000"), "DEFINED", null),
                        new MetricValue("ROA", new BigDecimal("8.200000"), "DEFINED", null),
                        new MetricValue("DEBT_TO_EQUITY", new BigDecimal("0.450000"), "DEFINED", null),
                        new MetricValue("EQUITY_ATTRIBUTABLE_TO_PARENT", new BigDecimal("50000000000.000000"),
                                "DEFINED", null),
                        new MetricValue("TOTAL_DEBT", new BigDecimal("15000000000.000000"), "DEFINED", null),
                        new MetricValue("CASH_AND_EQUIVALENTS", new BigDecimal("8000000000.000000"), "DEFINED", null),
                        new MetricValue("EBITDA", new BigDecimal("3000000000.000000"), "DEFINED", null));
                var incoming = new IncomingFundamentalReport("FINVERA_FIXTURE", symbol, "QUARTER", fiscalYear, q,
                        periodStart, periodEnd, "CONSOLIDATED", "REVIEWED", "VND", 1,
                        "fundamental-metric-catalog-v1",
                        periodEnd.plusDays(20).atStartOfDay(ZoneOffset.UTC).toInstant(), metrics, false, null);
                var result = ingestion.ingestFundamentalReport(incoming);
                if (result.status() != IngestionStatus.ACCEPTED) {
                    throw new IllegalStateException("Seed report rejected: " + result.reasonCode());
                }
            }
        }
    }
}
