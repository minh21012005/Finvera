package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.model.MarketTypes;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.entity.SectorReferenceEntity;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.SectorReferenceRepository;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingDailyBar;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingFundamentalReport;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionStatus;
import com.minhnb.finvera_be.stock.service.StockIngestionService.MetricValue;
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
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * T064 [G-04, FR-009]: with {@code finvera.stock.provider.sector-basis-enabled=true} and a real
 * sector with at least {@code MIN_SECTOR_CONSTITUENTS} (8) other classified peers, the sector
 * cross-section basis actually gets used instead of staying permanently empty. A separate
 * Spring context from {@link ValuationServiceTests} (different property set), so the default
 * (disabled) behavior those tests cover is unaffected.
 */
@ActiveProfiles("test")
@SpringBootTest
@TestPropertySource(properties = "finvera.stock.provider.sector-basis-enabled=true")
@Testcontainers
class ValuationServiceSectorBasisTests {

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
    @Autowired SectorReferenceRepository sectors;
    @Autowired StockIngestionService ingestion;
    @Autowired ValuationService valuation;

    @Test
    void usesTheSectorBasisWhenEightOrMoreClassifiedPeersExist() {
        UUID sectorId = sectors.save(new SectorReferenceEntity(
                UUID.randomUUID(), "KBS_INDUSTRY", "4.0.6", "3", "Bat dong san", null)).getId();

        seedClassifiedInstrument("SEC00", sectorId, new BigDecimal("40000.000000"));
        for (int i = 1; i <= 8; i++) {
            seedClassifiedInstrument("SEC0" + i, sectorId, new BigDecimal("40000.000000").add(BigDecimal.valueOf(i * 100)));
        }

        var result = valuation.findBySymbol("SEC00").orElseThrow();

        assertThat(result.usedSector()).isTrue();
        assertThat(result.sectorConstituentCount()).isNotNull().isGreaterThanOrEqualTo(8);
        assertThat(result.sector()).isEqualTo("3");
        assertThat(result.sectorScheme()).isEqualTo("KBS_INDUSTRY");
    }

    @Test
    void staysOnOwnHistoryOnlyBelowTheEightConstituentFloor() {
        UUID sectorId = sectors.save(new SectorReferenceEntity(
                UUID.randomUUID(), "KBS_INDUSTRY", "4.0.6", "9", "Thin sector", null)).getId();

        seedClassifiedInstrument("THIN0", sectorId, new BigDecimal("10000.000000"));
        for (int i = 1; i <= 3; i++) {
            seedClassifiedInstrument("THIN" + i, sectorId, new BigDecimal("10000.000000"));
        }

        var result = valuation.findBySymbol("THIN0").orElseThrow();

        assertThat(result.usedSector()).isFalse();
        assertThat(result.reasonCodes()).contains("SECTOR_BASIS_INSUFFICIENT");
    }

    private UUID seedClassifiedInstrument(String symbol, UUID sectorId, BigDecimal price) {
        UUID instrumentId = UUID.randomUUID();
        instruments.save(new MarketInstrumentEntity(instrumentId, null, MarketTypes.Venue.HOSE.name(), symbol,
                "EQUITY", LocalDate.of(2010, 1, 1), null, "ACTIVE", "FINVERA_FIXTURE", "v1"));
        profiles.save(new EquityProfileEntity(UUID.randomUUID(), instrumentId, "CTCP " + symbol, symbol,
                sectorId, 1_000_000_000L, new BigDecimal("0.800000"), "LISTED",
                LocalDate.of(2020, 1, 1), null, "FINVERA_FIXTURE", "1", null));

        LocalDate tradingDate = LocalDate.of(2026, 8, 14);
        var barResult = ingestion.ingestDailyBar(new IncomingDailyBar("FINVERA_FIXTURE", symbol, tradingDate,
                tradingDate.atStartOfDay(ZoneOffset.UTC).toInstant(), price.subtract(new BigDecimal("500.000000")),
                price.add(new BigDecimal("500.000000")), price.subtract(new BigDecimal("600.000000")), price,
                1_000_000L, null, "RAW", false));
        if (barResult.status() != IngestionStatus.ACCEPTED) {
            throw new IllegalStateException("Seed bar rejected: " + barResult.reasonCode());
        }

        for (int q = 1; q <= 4; q++) {
            LocalDate periodStart = LocalDate.of(2025, (q - 1) * 3 + 1, 1);
            LocalDate periodEnd = periodStart.plusMonths(3).minusDays(1);
            List<MetricValue> metrics = List.of(
                    new MetricValue("EPS", new BigDecimal("500.000000"), "DEFINED", null),
                    new MetricValue("NET_PROFIT", new BigDecimal("2000000000.000000"), "DEFINED", null));
            var reportResult = ingestion.ingestFundamentalReport(new IncomingFundamentalReport(
                    "FINVERA_FIXTURE", symbol, "QUARTER", 2025, q, periodStart, periodEnd, "CONSOLIDATED",
                    "REVIEWED", "VND", 1, "fundamental-metric-catalog-v1",
                    periodEnd.plusDays(20).atStartOfDay(ZoneOffset.UTC).toInstant(), metrics, false, null));
            if (reportResult.status() != IngestionStatus.ACCEPTED) {
                throw new IllegalStateException("Seed report rejected: " + reportResult.reasonCode());
            }
        }
        return instrumentId;
    }
}
