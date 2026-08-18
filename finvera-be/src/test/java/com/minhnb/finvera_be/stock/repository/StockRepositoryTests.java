package com.minhnb.finvera_be.stock.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import com.minhnb.finvera_be.stock.entity.FundamentalReportEntity;
import com.minhnb.finvera_be.stock.entity.FundamentalReportMetricEntity;
import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorResultEntity;
import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorValueEntity;
import com.minhnb.finvera_be.stock.entity.ValuationAssessmentEntity;
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

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class StockRepositoryTests {

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
    @Autowired com.minhnb.finvera_be.market.repository.MarketObservationRepository ingestionObservations;
    @Autowired EquityDailyBarRepository dailyBars;
    @Autowired FundamentalReportRepository reports;
    @Autowired FundamentalReportMetricRepository reportMetrics;
    @Autowired TechnicalIndicatorResultRepository technicalResults;
    @Autowired TechnicalIndicatorValueRepository technicalValues;
    @Autowired ValuationAssessmentRepository valuationAssessments;

    @Test
    void roundTripsUtcInstantsAndExactDecimalsOnADailyBar() {
        UUID instrumentId = saveInstrument("RTFPT");
        UUID ingestionId = saveIngestion("DAILY_BAR", "RTFPT|2026-08-14");
        Instant observedAt = Instant.parse("2026-08-14T08:15:00Z");

        dailyBars.save(new EquityDailyBarEntity(UUID.randomUUID(), instrumentId, ingestionId, null,
                LocalDate.of(2026, 8, 14), new BigDecimal("122500.000000"), new BigDecimal("123800.000000"),
                new BigDecimal("122300.000000"), new BigDecimal("123600.000000"), null, null, "RAW",
                2_270_000L, new BigDecimal("280457200000.0000"), "FINVERA_FIXTURE", observedAt,
                observedAt.plusSeconds(1), 1, true, null, null));

        var loaded = dailyBars.findFirstByInstrumentIdAndTradingDateAndSourceAndCurrentTrue(
                instrumentId, LocalDate.of(2026, 8, 14), "FINVERA_FIXTURE").orElseThrow();

        assertThat(loaded.getObservedAt()).isEqualTo(observedAt);
        assertThat(loaded.getClosePrice()).isEqualByComparingTo("123600.000000");
        assertThat(loaded.isCurrent()).isTrue();
    }

    @Test
    void aCorrectionSupersedesTheCurrentBarWithoutOverwritingIt() {
        UUID instrumentId = saveInstrument("RTCORR");
        UUID firstIngestion = saveIngestion("DAILY_BAR", "RTCORR|2026-08-14|1");
        UUID firstBarId = UUID.randomUUID();
        Instant observedAt = Instant.parse("2026-08-14T08:15:00Z");
        dailyBars.save(new EquityDailyBarEntity(firstBarId, instrumentId, firstIngestion, null,
                LocalDate.of(2026, 8, 14), new BigDecimal("100.000000"), new BigDecimal("101.000000"),
                new BigDecimal("99.000000"), new BigDecimal("100.500000"), null, null, "RAW", 1_000_000L, null,
                "FINVERA_FIXTURE", observedAt, observedAt.plusSeconds(1), 1, true, null, null));

        var toSupersede = dailyBars.findById(firstBarId).orElseThrow();
        toSupersede.markSuperseded();
        dailyBars.save(toSupersede);

        UUID secondIngestion = saveIngestion("DAILY_BAR", "RTCORR|2026-08-14|2");
        dailyBars.save(new EquityDailyBarEntity(UUID.randomUUID(), instrumentId, secondIngestion, null,
                LocalDate.of(2026, 8, 14), new BigDecimal("100.000000"), new BigDecimal("102.000000"),
                new BigDecimal("99.000000"), new BigDecimal("101.500000"), null, null, "RAW", 1_050_000L, null,
                "FINVERA_FIXTURE", observedAt, observedAt.plusSeconds(2), 2, true, firstBarId, null));

        var current = dailyBars.findFirstByInstrumentIdAndTradingDateAndSourceAndCurrentTrue(
                instrumentId, LocalDate.of(2026, 8, 14), "FINVERA_FIXTURE").orElseThrow();
        assertThat(current.getRevision()).isEqualTo(2);
        assertThat(current.getSupersedesId()).isEqualTo(firstBarId);
        assertThat(current.getClosePrice()).isEqualByComparingTo("101.500000");

        var superseded = dailyBars.findById(firstBarId).orElseThrow();
        assertThat(superseded.isCurrent()).isFalse();
        assertThat(superseded.getClosePrice()).isEqualByComparingTo("100.500000");
    }

    @Test
    void preservesTheThreeStateApplicabilityDistinctionOnFundamentalMetrics() {
        UUID instrumentId = saveInstrument("RT3STATE");
        UUID ingestionId = saveIngestion("FUNDAMENTAL_REPORT", "RT3STATE|2026-Q2");
        UUID reportId = UUID.randomUUID();
        Instant reportObservedAt = Instant.parse("2026-07-28T02:05:00Z");
        reports.save(new FundamentalReportEntity(reportId, instrumentId, ingestionId, "QUARTER",
                (short) 2026, (short) 2, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30), "CONSOLIDATED",
                "REVIEWED", "VND", 1, "fundamental-metric-catalog-v1", null, reportObservedAt,
                reportObservedAt.plusSeconds(5), "FINVERA_FIXTURE", 1, true, null, null));
        reportMetrics.save(new FundamentalReportMetricEntity(
                reportId, "REVENUE", new BigDecimal("16250000000000.000000"), "DEFINED", null));
        reportMetrics.save(new FundamentalReportMetricEntity(
                reportId, "DIVIDEND_PER_SHARE", null, "NOT_APPLICABLE", "NO_DIVIDEND_DECLARED"));
        // MISSING is expressed by the row's absence, not by a stored value; assert only DEFINED/NOT_APPLICABLE rows exist.
        var rows = reportMetrics.findByReportId(reportId);

        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(row -> row.getMetricCode().equals("REVENUE"))
                .allSatisfy(row -> {
                    assertThat(row.getApplicability()).isEqualTo("DEFINED");
                    assertThat(row.getValue()).isEqualByComparingTo("16250000000000.000000");
                });
        assertThat(rows).filteredOn(row -> row.getMetricCode().equals("DIVIDEND_PER_SHARE"))
                .allSatisfy(row -> {
                    assertThat(row.getApplicability()).isEqualTo("NOT_APPLICABLE");
                    assertThat(row.getValue()).isNull();
                    assertThat(row.getQualityReason()).isEqualTo("NO_DIVIDEND_DECLARED");
                });
        assertThat(reportMetrics.findByReportId(reportId))
                .noneMatch(row -> row.getMetricCode().equals("EPS"));
    }

    @Test
    void roundTripsScaleTwelveIndicatorComponentsAndReasonCodeArrays() {
        UUID instrumentId = saveInstrument("RTSCALE12");
        UUID resultId = UUID.randomUUID();
        technicalResults.save(new TechnicalIndicatorResultEntity(resultId, instrumentId, "RSI14",
                "technical-indicators-v1", LocalDate.of(2026, 8, 14), LocalDate.of(2025, 9, 1),
                LocalDate.of(2026, 8, 14), 250, "a".repeat(64), "ADJUSTED", "CURRENT", null,
                Instant.parse("2026-08-14T08:20:00Z"), true, null));
        technicalValues.save(new TechnicalIndicatorValueEntity(
                resultId, "VALUE", new BigDecimal("68.769871598355"), "POINTS", "DEFINED", null));

        var loaded = technicalValues.findByResultId(resultId);
        assertThat(loaded).hasSize(1);
        assertThat(loaded.getFirst().getValue()).isEqualByComparingTo("68.769871598355");
        assertThat(loaded.getFirst().getValue().scale()).isEqualTo(12);

        UUID instrumentId2 = saveInstrument("RTARRAY");
        valuationAssessments.save(new ValuationAssessmentEntity(UUID.randomUUID(), instrumentId2,
                LocalDate.of(2026, 8, 14), Instant.parse("2026-08-14T08:20:00Z"), "valuation-v1", null, null,
                null, null, false, false, null, null, null, "PARTIAL",
                List.of("NO_COMPARISON_BASIS", "HISTORY_BASIS_INSUFFICIENT"),
                Instant.parse("2026-08-14T08:20:01Z"), true, null));
        var withheld = valuationAssessments
                .findFirstByInstrumentIdAndRuleVersionAndAsOfTradingDateAndCurrentTrue(
                        instrumentId2, "valuation-v1", LocalDate.of(2026, 8, 14))
                .orElseThrow();
        assertThat(withheld.getReasonCodes()).containsExactly("NO_COMPARISON_BASIS", "HISTORY_BASIS_INSUFFICIENT");
        assertThat(withheld.getClassification()).isNull();
    }

    private UUID saveInstrument(String symbol) {
        UUID id = UUID.randomUUID();
        instruments.save(new MarketInstrumentEntity(id, null, "HOSE", symbol, "EQUITY",
                LocalDate.of(2010, 1, 1), null, "ACTIVE", "FINVERA_FIXTURE", "v1"));
        return id;
    }

    private UUID saveIngestion(String dataset, String subject) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-14T08:15:00Z");
        ingestionObservations.save(new com.minhnb.finvera_be.market.entity.MarketObservationEntity(
                id, "FINVERA_FIXTURE", dataset, subject, LocalDate.of(2026, 8, 14), now, now,
                now.plusSeconds(1), null, String.format("%064x", subject.hashCode() & 0xffffffffL),
                "ACCEPTED", null, null));
        return id;
    }
}
