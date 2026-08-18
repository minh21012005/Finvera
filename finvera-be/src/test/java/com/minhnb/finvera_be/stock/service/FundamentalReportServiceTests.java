package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.model.MarketTypes;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.stock.entity.FundamentalSummaryEntity;
import com.minhnb.finvera_be.stock.repository.FundamentalSummaryInputRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalSummaryRepository;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingFundamentalReport;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionStatus;
import com.minhnb.finvera_be.stock.service.StockIngestionService.MetricValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
 * T049 [FR-007, FR-014, DATA-006, DATA-009; NFR-003]
 * Application integration tests for {@link FundamentalReportService}'s
 * persistence of {@code fundamental_summary}: until this test existed, the
 * service computed a summary on every read but never persisted it — the
 * repositories T010 built for this table were wired into the constructor and
 * never called. These tests exercise the real database path this task's own
 * Verify clause requires ("preserve the superseded revision as queryable and
 * show the corrected figures with a new as-of indication").
 */
@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class FundamentalReportServiceTests {

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
    @Autowired StockIngestionService ingestion;
    @Autowired FundamentalReportService fundamentals;
    @Autowired FundamentalSummaryRepository summaries;
    @Autowired FundamentalSummaryInputRepository summaryInputs;

    @Test
    void unknownSymbolIsAbsentNotFabricated() {
        assertThat(fundamentals.findBySymbol("ZZZUNKNOWN")).isEmpty();
    }

    @Test
    void noAcceptedReportsYieldsUnavailableWithoutPersistingASummary() {
        UUID instrumentId = saveInstrument("STF01");

        var result = fundamentals.findBySymbol("STF01").orElseThrow();

        assertThat(result.periodType()).isNull();
        assertThat(summaries.findFirstByInstrumentIdAndRuleVersionOrderByAsOfTradingDateDescCalculatedAtDesc(
                instrumentId, "fundamental-summary-v1")).isEmpty();
    }

    @Test
    void persistsAnImmutableSummaryLinkedToContributingReportsAndIsIdempotentOnReplay() {
        UUID instrumentId = saveInstrument("STF02");
        ingestFourQuarters("STF02", 2025, new BigDecimal("500.000000"));

        var first = fundamentals.findBySymbol("STF02").orElseThrow();
        assertThat(first.basisPeriodLabel()).isEqualTo("2025-Q4");

        var persisted = summaries
                .findFirstByInstrumentIdAndRuleVersionOrderByAsOfTradingDateDescCalculatedAtDesc(
                        instrumentId, "fundamental-summary-v1")
                .orElseThrow();
        assertThat(persisted.getBasisPeriodLabel()).isEqualTo("2025-Q4");
        assertThat(summaryInputs.findBySummaryId(persisted.getId())).hasSize(4);

        // A second read of unchanged accepted reports must not create a new revision.
        fundamentals.findBySymbol("STF02");
        var stillCurrent = summaries
                .findFirstByInstrumentIdAndRuleVersionOrderByAsOfTradingDateDescCalculatedAtDesc(
                        instrumentId, "fundamental-summary-v1")
                .orElseThrow();
        assertThat(stillCurrent.getId()).isEqualTo(persisted.getId());
    }

    @Test
    void restatementProducesANewSummaryRevisionWhileThePreviousStaysQueryable() {
        saveInstrument("STF03");
        ingestFourQuarters("STF03", 2025, new BigDecimal("500.000000"));
        var beforeRestatement = fundamentals.findBySymbol("STF03").orElseThrow();
        UUID previousSummaryId = summaries
                .findFirstByInstrumentIdAndRuleVersionOrderByAsOfTradingDateDescCalculatedAtDesc(
                        resolveInstrumentId("STF03"), "fundamental-summary-v1")
                .orElseThrow().getId();
        assertThat(beforeRestatement.metrics()).isNotEmpty();

        // Restate Q4 with a materially different EPS.
        var restated = ingestion.ingestFundamentalReport(quarterlyReport("STF03", 2025, 4,
                Instant.parse("2026-03-01T02:00:00Z"), new BigDecimal("999.000000"), true, "AUDIT_ADJUSTMENT"));
        assertThat(restated.status()).isEqualTo(IngestionStatus.CORRECTED);

        var afterRestatement = fundamentals.findBySymbol("STF03").orElseThrow();
        assertThat(afterRestatement.restated()).isTrue();

        UUID newSummaryId = summaries
                .findFirstByInstrumentIdAndRuleVersionOrderByAsOfTradingDateDescCalculatedAtDesc(
                        resolveInstrumentId("STF03"), "fundamental-summary-v1")
                .orElseThrow().getId();
        assertThat(newSummaryId).isNotEqualTo(previousSummaryId);

        // The superseded revision remains queryable, not deleted (DATA-006).
        assertThat(summaries.findById(previousSummaryId)).isPresent();
        FundamentalSummaryEntity newSummary = summaries.findById(newSummaryId).orElseThrow();
        assertThat(newSummary.getSupersedesId()).isEqualTo(previousSummaryId);
    }

    private UUID resolveInstrumentId(String symbol) {
        return instruments.findAll().stream()
                .filter(i -> symbol.equals(i.getSymbol())).findFirst().orElseThrow().getId();
    }

    private void ingestFourQuarters(String symbol, int fiscalYear, BigDecimal epsPerQuarter) {
        for (int q = 1; q <= 4; q++) {
            var result = ingestion.ingestFundamentalReport(
                    quarterlyReport(symbol, fiscalYear, q, null, epsPerQuarter, false, null));
            if (result.status() != IngestionStatus.ACCEPTED) {
                throw new IllegalStateException("Seed report rejected: " + result.reasonCode());
            }
        }
    }

    private static IncomingFundamentalReport quarterlyReport(String symbol, int fiscalYear, int fiscalQuarter,
            Instant observedAtOverride, BigDecimal eps, boolean isRestatement, String restatementReason) {
        int startMonth = (fiscalQuarter - 1) * 3 + 1;
        LocalDate periodStart = LocalDate.of(fiscalYear, startMonth, 1);
        LocalDate periodEnd = periodStart.plusMonths(3).minusDays(1);
        Instant observedAt = observedAtOverride != null
                ? observedAtOverride
                : periodEnd.plusDays(20).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        List<MetricValue> metrics = List.of(
                new MetricValue("REVENUE", new BigDecimal("10000000000.000000"), "DEFINED", null),
                new MetricValue("NET_PROFIT", new BigDecimal("2000000000.000000"), "DEFINED", null),
                new MetricValue("EPS", eps, "DEFINED", null),
                new MetricValue("EQUITY_ATTRIBUTABLE_TO_PARENT", new BigDecimal("50000000000.000000"), "DEFINED", null),
                new MetricValue("TOTAL_DEBT", new BigDecimal("15000000000.000000"), "DEFINED", null),
                new MetricValue("CASH_AND_EQUIVALENTS", new BigDecimal("8000000000.000000"), "DEFINED", null),
                new MetricValue("EBITDA", new BigDecimal("3000000000.000000"), "DEFINED", null),
                new MetricValue("DIVIDEND_PER_SHARE", new BigDecimal("200.000000"), "DEFINED", null),
                new MetricValue("ROE", new BigDecimal("18.500000"), "DEFINED", null),
                new MetricValue("ROA", new BigDecimal("9.200000"), "DEFINED", null));
        return new IncomingFundamentalReport(
                "FINVERA_FIXTURE", symbol, "QUARTER", fiscalYear, fiscalQuarter,
                periodStart, periodEnd, "CONSOLIDATED", "REVIEWED", "VND",
                1, "fundamental-metric-catalog-v1", observedAt, metrics, isRestatement, restatementReason);
    }

    private UUID saveInstrument(String symbol) {
        UUID id = UUID.randomUUID();
        instruments.save(new MarketInstrumentEntity(id, null, MarketTypes.Venue.HOSE.name(), symbol, "EQUITY",
                LocalDate.of(2010, 1, 1), null, "ACTIVE", "FINVERA_FIXTURE", "v1"));
        return id;
    }
}
