package com.minhnb.finvera_be.stock.operations;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.model.MarketTypes;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
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
 * T028 [NFR-002, DATA-001]: a whole selected category with zero accepted
 * upstream rows across every candidate must be disclosed as
 * {@code UNAVAILABLE}, and must be distinguishable from a category that was
 * fully evaluated and simply matched zero stocks (a legitimate answer, not a
 * data-quality problem). Collapsing the two into one "0 matches" response
 * would hide a real failure behind a normal empty result.
 */
@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class ScreenerFailureTests {

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
    @Autowired ScreenerService screener;

    @Test
    void aCategoryWithZeroAcceptedUpstreamRowsIsDisclosedUnavailableNotAsAZeroMatchAnswer() {
        // Candidates exist and satisfy Price, but none has ever had a technical
        // indicator computed — the TECHNICAL category itself is the failure, not
        // "no stock happened to match the RSI range". The [40000,70000] price
        // band is deliberately disjoint from the other test method's ~126 close,
        // since this class's Testcontainers Postgres instance (and therefore the
        // screener's candidate universe) is shared across both test methods with
        // no reset between them, and JUnit does not guarantee method order.
        seedListedInstrument("SFT01");
        seedDailyBar("SFT01", new BigDecimal("50000.000000"));
        seedListedInstrument("SFT02");
        seedDailyBar("SFT02", new BigDecimal("60000.000000"));

        var criteria = new ScreenCriteria(null,
                new PriceFilter(new BigDecimal("40000"), new BigDecimal("70000"), null, null),
                new TechnicalFilter(BigDecimal.ZERO, new BigDecimal("100"), null, null, null, null, null, null,
                        null, null),
                null);
        var result = screener.execute(criteria, SortField.SYMBOL, SortDirection.ASC, 50, 0);

        var technicalDisclosure = result.categoryDisclosures().stream()
                .filter(d -> d.category().equals("TECHNICAL")).findFirst().orElseThrow();
        assertThat(technicalDisclosure.status().name()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void aFullyEvaluatedCategoryThatSimplyMatchesNothingIsCurrentNotUnavailable() {
        // The same TECHNICAL category, but this time it is genuinely evaluated
        // (RSI is computed) and simply excludes the candidate by range — a
        // legitimate zero-match answer, not a data-quality failure.
        String symbol = "SFT03";
        seedListedInstrument(symbol);
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

        // A monotonically rising series drives RSI to 100 (Feature 002
        // technical-indicators-v1 required-test-vector convention); an RSI
        // range that cannot include 100 forces a genuine, evaluated non-match.
        // The [100,200] price band excludes the other test method's ~50000/60000
        // candidates, for the same order-independence reason noted there.
        var criteria = new ScreenCriteria(null,
                new PriceFilter(new BigDecimal("100"), new BigDecimal("200"), null, null),
                new TechnicalFilter(BigDecimal.ZERO, new BigDecimal("10"), null, null, null, null, null, null,
                        null, null),
                null);
        var result = screener.execute(criteria, SortField.SYMBOL, SortDirection.ASC, 50, 0);

        assertThat(result.matches()).noneSatisfy(m -> assertThat(m.symbol()).isEqualTo(symbol));
        var technicalDisclosure = result.categoryDisclosures().stream()
                .filter(d -> d.category().equals("TECHNICAL")).findFirst().orElseThrow();
        assertThat(technicalDisclosure.status().name()).isEqualTo("CURRENT");
    }

    private void seedListedInstrument(String symbol) {
        UUID id = UUID.randomUUID();
        instruments.save(new MarketInstrumentEntity(id, null, MarketTypes.Venue.HOSE.name(), symbol, "EQUITY",
                LocalDate.of(2010, 1, 1), null, "ACTIVE", "FINVERA_FIXTURE", "v1"));
        profiles.save(new EquityProfileEntity(UUID.randomUUID(), id, "CTCP " + symbol, symbol,
                null, 1_000_000L, new BigDecimal("0.800000"), "LISTED",
                LocalDate.of(2020, 1, 1), null, "FINVERA_FIXTURE", "1", null));
    }

    private void seedDailyBar(String symbol, BigDecimal close) {
        var incoming = new IncomingDailyBar("FINVERA_FIXTURE", symbol, LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 8, 14).atStartOfDay(ZoneOffset.UTC).toInstant(),
                close.subtract(new BigDecimal("500.000000")), close.add(new BigDecimal("500.000000")),
                close.subtract(new BigDecimal("600.000000")), close, 1_000_000L, null, "RAW", false);
        var result = ingestion.ingestDailyBar(incoming);
        if (result.status() != IngestionStatus.ACCEPTED) {
            throw new IllegalStateException("Seed bar rejected: " + result.reasonCode());
        }
    }
}
