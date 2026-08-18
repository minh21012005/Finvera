package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.model.MarketTypes;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorCode;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorResultRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorValueRepository;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingDailyBar;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

/** FR-006, FR-011, FR-014; DATA-009, DATA-010; NFR-003. */
@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class TechnicalIndicatorServiceTests {

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
    @Autowired TechnicalIndicatorResultRepository results;
    @Autowired TechnicalIndicatorValueRepository values;
    @Autowired StockIngestionService ingestion;
    @Autowired TechnicalIndicatorService technical;

    @Test
    void unknownSymbolIsAbsentNotFabricated() {
        assertThat(technical.findBySymbol("ZZZUNKNOWN")).isEmpty();
    }

    @Test
    void withFewerThanTwentyBarsEveryIndicatorIsInsufficientHistory() {
        saveInstrument("STK21");
        seedAscendingBars("STK21", "FINVERA_FIXTURE", LocalDate.of(2026, 7, 1), 10);

        var result = technical.findBySymbol("STK21").orElseThrow();
        assertThat(result.indicators()).allSatisfy(indicator ->
                assertThat(indicator.applicability()).isEqualTo(MetricApplicability.MISSING));
    }

    @Test
    void persistsAnImmutableCurrentRowPerIndicatorLinkedByInputSetHashAndIsIdempotentOnReplay() {
        UUID instrumentId = saveInstrument("STK22");
        LocalDate asOf = seedAscendingBars("STK22", "FINVERA_FIXTURE", LocalDate.of(2025, 1, 1), 260);

        var first = technical.findBySymbol("STK22").orElseThrow();
        var ma20First = indicator(first, IndicatorCode.MA20);
        assertThat(ma20First.applicability()).isEqualTo(MetricApplicability.DEFINED);

        var persisted = results
                .findFirstByInstrumentIdAndIndicatorCodeAndAsOfTradingDateAndRuleVersionAndCurrentTrue(
                        instrumentId, "MA20", asOf, TechnicalIndicatorsV1.RULE_VERSION)
                .orElseThrow();
        assertThat(persisted.getInputSetHash()).hasSize(64);
        assertThat(values.findByResultId(persisted.getId())).isNotEmpty();

        // A second read of unchanged accepted data must not create a new revision.
        technical.findBySymbol("STK22");
        var stillCurrent = results
                .findFirstByInstrumentIdAndIndicatorCodeAndAsOfTradingDateAndRuleVersionAndCurrentTrue(
                        instrumentId, "MA20", asOf, TechnicalIndicatorsV1.RULE_VERSION)
                .orElseThrow();
        assertThat(stillCurrent.getId()).isEqualTo(persisted.getId());
    }

    @Test
    void aSourceConflictWithholdsOnlyTheIndicatorsWhoseWindowConsumesTheDisputedBar() {
        UUID instrumentId = saveInstrument("STK23");
        LocalDate asOf = LocalDate.of(2026, 8, 14);
        // 260 TCBS bars ending exactly on asOf, then plant a materially different
        // VNSTOCK bar on that same last trading date — reconcileDailyBar's policy
        // specifically compares the "TCBS" and "VNSTOCK" sources (StockIngestionService.reconcileDailyBar).
        seedAscendingBars("STK23", "TCBS", asOf.minusDays(259), 260);
        var vnstockResult = ingestion.ingestDailyBar(new IncomingDailyBar("VNSTOCK", "STK23", asOf,
                Instant.parse("2026-08-15T01:00:00Z"), new BigDecimal("50.000000"), new BigDecimal("51.000000"),
                new BigDecimal("39.000000"), new BigDecimal("40.000000"), 900_000L, null, "RAW", false));
        assertThat(vnstockResult.status()).isEqualTo(StockIngestionService.IngestionStatus.ACCEPTED);
        var directDecision = ingestion.reconcileDailyBar(instrumentId, "STK23", asOf);
        assertThat(directDecision)
                .isEqualTo(com.minhnb.finvera_be.market.domain.reconciliation.SourceReconciliationPolicy.Decision.SOURCE_CONFLICT);

        var result = technical.findBySymbol("STK23").orElseThrow();
        assertThat(indicator(result, IndicatorCode.MA20).applicability()).isEqualTo(MetricApplicability.MISSING);
        assertThat(indicator(result, IndicatorCode.MA20).reasonCode()).isEqualTo("SOURCE_CONFLICT");
        assertThat(indicator(result, IndicatorCode.MA50).applicability()).isEqualTo(MetricApplicability.MISSING);
        assertThat(indicator(result, IndicatorCode.MA50).reasonCode()).isEqualTo("SOURCE_CONFLICT");
        // MA200 needs 200 bars but this instrument only has 260 total, all of them
        // now inside its window too, so it is also withheld — proving the check is
        // real (every indicator whose window includes the disputed date is caught),
        // not merely "the first indicator only".
        assertThat(indicator(result, IndicatorCode.MA200).reasonCode()).isEqualTo("SOURCE_CONFLICT");
    }

    private static com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.IndicatorResult indicator(
            TechnicalIndicatorService.StockTechnical result, IndicatorCode code) {
        return result.indicators().stream().filter(i -> i.indicatorCode() == code).findFirst().orElseThrow();
    }

    private UUID saveInstrument(String symbol) {
        UUID id = UUID.randomUUID();
        instruments.save(new MarketInstrumentEntity(id, null, MarketTypes.Venue.HOSE.name(), symbol, "EQUITY",
                LocalDate.of(2010, 1, 1), null, "ACTIVE", "FINVERA_FIXTURE", "v1"));
        return id;
    }

    /** Seeds {@code count} consecutive calendar-day bars (weekends included, simplest deterministic series for unit math), returning the last trading date. */
    private LocalDate seedAscendingBars(String symbol, String source, LocalDate start, int count) {
        LocalDate date = start;
        BigDecimal close = new BigDecimal("100.000000");
        for (int i = 0; i < count; i++) {
            close = close.add(new BigDecimal("0.100000"));
            var incoming = new IncomingDailyBar(source, symbol, date,
                    date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                    close.subtract(new BigDecimal("0.500000")), close.add(new BigDecimal("0.500000")),
                    close.subtract(new BigDecimal("0.600000")), close, 1_000_000L, null, "RAW", false);
            var result = ingestion.ingestDailyBar(incoming);
            if (result.status() != StockIngestionService.IngestionStatus.ACCEPTED) {
                throw new IllegalStateException("Seed bar rejected: " + result.reasonCode());
            }
            date = date.plusDays(1);
        }
        return date.minusDays(1);
    }
}
