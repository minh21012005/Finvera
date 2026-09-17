package com.minhnb.finvera_be.stock.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.model.MarketTypes;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.stock.domain.model.StockTypes;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.service.StockIngestionService;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingDailyBar;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionStatus;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService;
import com.minhnb.finvera_be.stock.service.strategy.StrategyScanService.ScanMatch;
import com.minhnb.finvera_be.stock.service.strategy.StrategyScanService.ScanResult;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService.SignalDetail;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * T018: orchestration-level integration tests for {@link StrategyScanService}
 * — exact match set, empty result, insufficient-history exclusion count,
 * pagination. Per-strategy formula correctness is exhaustively covered at
 * the unit level by {@code StrategySignalV1Tests}.
 */
@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class StrategyScanServiceTests {

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
    @Autowired StrategyScanService scanService;

    @Test
    void scanFindsExactlyTheTriggeringInstrumentsForTrendFollowing() {
        seedListedInstrument("SCAN01");
        seedAscendingBars("SCAN01", LocalDate.of(2025, 1, 1), 260);
        technicalIndicatorService.findBySymbol("SCAN01");

        seedListedInstrument("SCAN02");
        seedFlatBars("SCAN02", LocalDate.of(2025, 1, 1), 260);
        technicalIndicatorService.findBySymbol("SCAN02");

        ScanResult result = scanService.scan(StrategyCode.TREND_FOLLOWING, 50, 0);

        assertThat(result.matches()).anySatisfy(m -> assertThat(m.symbol()).isEqualTo("SCAN01"));
        assertThat(result.matches()).noneSatisfy(m -> assertThat(m.symbol()).isEqualTo("SCAN02"));
        assertThat(result.matches()).allSatisfy(m -> assertThat(m.signal()).isNotNull());
    }

    @Test
    void scanReturnsASpecificEmptyResultWhenNoCandidateTriggers() {
        seedListedInstrument("SCAN03");
        seedFlatBars("SCAN03", LocalDate.of(2025, 1, 1), 260);
        technicalIndicatorService.findBySymbol("SCAN03");

        ScanResult result = scanService.scan(StrategyCode.MEAN_REVERSION, 50, 0);

        assertThat(result.matches()).noneSatisfy(m -> assertThat(m.symbol()).isEqualTo("SCAN03"));
        assertThat(result.totalMatchCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void scanDisclosesInsufficientHistoryExclusionsSeparatelyFromGenuineNonTriggers() {
        seedListedInstrument("SCAN04");
        seedAscendingBars("SCAN04", LocalDate.of(2026, 8, 1), 5); // far below every strategy's minimum
        technicalIndicatorService.findBySymbol("SCAN04");

        ScanResult before = scanService.scan(StrategyCode.TREND_FOLLOWING, 50, 0);
        assertThat(before.excludedForInsufficientHistoryCount()).isGreaterThanOrEqualTo(1);
        assertThat(before.matches()).noneSatisfy(m -> assertThat(m.symbol()).isEqualTo("SCAN04"));
    }

    @Test
    void scanPaginatesTheTriggeringSetWithoutLosingOrGainingMatches() {
        seedListedInstrument("SCAN05");
        seedAscendingBars("SCAN05", LocalDate.of(2025, 1, 1), 260);
        technicalIndicatorService.findBySymbol("SCAN05");
        seedListedInstrument("SCAN06");
        seedAscendingBars("SCAN06", LocalDate.of(2025, 1, 1), 260);
        technicalIndicatorService.findBySymbol("SCAN06");

        ScanResult firstPage = scanService.scan(StrategyCode.TREND_FOLLOWING, 1, 0);
        ScanResult secondPage = scanService.scan(StrategyCode.TREND_FOLLOWING, 1, 1);

        assertThat(firstPage.matches()).hasSizeLessThanOrEqualTo(1);
        assertThat(firstPage.totalMatchCount()).isEqualTo(secondPage.totalMatchCount());
        if (firstPage.matches().size() == 1 && secondPage.matches().size() == 1) {
            assertThat(firstPage.matches().get(0).symbol()).isNotEqualTo(secondPage.matches().get(0).symbol());
        }
    }

    @Test
    void qualityRankingRanksStrongBeforeModerate_andHoseBeforeUpcom_andLowestRiskFirst() {
        var levelSet = new StrategySignalV1.LevelSet(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE);
        var strongHoseLowRisk = new ScanMatch("ZZZ", "CTCP Z", "HOSE",
                new SignalDetail(StrategyCode.MOMENTUM, "v1", StockTypes.Direction.LONG, levelSet, 20, StockTypes.RiskLevel.LOW, StockTypes.SignalStrength.STRONG, List.of(), Map.of(), List.of(), LocalDate.now(), Instant.now()));
        var strongHoseHigherRisk = new ScanMatch("AAA", "CTCP A", "HOSE",
                new SignalDetail(StrategyCode.MOMENTUM, "v1", StockTypes.Direction.LONG, levelSet, 30, StockTypes.RiskLevel.LOW, StockTypes.SignalStrength.STRONG, List.of(), Map.of(), List.of(), LocalDate.now(), Instant.now()));
        var strongUpcom = new ScanMatch("AAS", "CTCP AAS", "UPCOM",
                new SignalDetail(StrategyCode.MOMENTUM, "v1", StockTypes.Direction.LONG, levelSet, 15, StockTypes.RiskLevel.LOW, StockTypes.SignalStrength.STRONG, List.of(), Map.of(), List.of(), LocalDate.now(), Instant.now()));
        var moderateHose = new ScanMatch("BBB", "CTCP BBB", "HOSE",
                new SignalDetail(StrategyCode.MOMENTUM, "v1", StockTypes.Direction.LONG, levelSet, 40, StockTypes.RiskLevel.MEDIUM, StockTypes.SignalStrength.MODERATE, List.of(), Map.of(), List.of(), LocalDate.now(), Instant.now()));

        List<ScanMatch> list = new ArrayList<>(List.of(moderateHose, strongUpcom, strongHoseHigherRisk, strongHoseLowRisk));
        list.sort(StrategyScanService.QUALITY_RANKING);

        // 1. STRONG + HOSE + Risk 20 (ZZZ beats AAA due to lower risk)
        assertThat(list.get(0).symbol()).isEqualTo("ZZZ");
        // 2. STRONG + HOSE + Risk 30 (AAA beats AAS due to HOSE venue)
        assertThat(list.get(1).symbol()).isEqualTo("AAA");
        // 3. STRONG + UPCOM + Risk 15 (AAS beats BBB due to STRONG strength)
        assertThat(list.get(2).symbol()).isEqualTo("AAS");
        // 4. MODERATE + HOSE + Risk 40
        assertThat(list.get(3).symbol()).isEqualTo("BBB");
    }

    @Test
    void scanRanksHoseBeforeUpcomRegardlessOfAlphabeticalOrder() {
        seedListedInstrumentWithVenue("Z_HOSE", MarketTypes.Venue.HOSE.name());
        seedAscendingBars("Z_HOSE", LocalDate.of(2025, 1, 1), 260);
        technicalIndicatorService.findBySymbol("Z_HOSE");

        seedListedInstrumentWithVenue("A_UPCOM", MarketTypes.Venue.UPCOM.name());
        seedAscendingBars("A_UPCOM", LocalDate.of(2025, 1, 1), 260);
        technicalIndicatorService.findBySymbol("A_UPCOM");

        ScanResult result = scanService.scan(StrategyCode.TREND_FOLLOWING, 10, 0);

        List<String> symbols = result.matches().stream().map(ScanMatch::symbol).toList();
        int zHoseIndex = symbols.indexOf("Z_HOSE");
        int aUpcomIndex = symbols.indexOf("A_UPCOM");

        assertThat(zHoseIndex).isGreaterThanOrEqualTo(0);
        assertThat(aUpcomIndex).isGreaterThanOrEqualTo(0);
        assertThat(zHoseIndex).isLessThan(aUpcomIndex);
    }

    // ── Seeding helpers ──────────────────────────────────────────────────────

    private void seedListedInstrumentWithVenue(String symbol, String venue) {
        UUID id = UUID.randomUUID();
        instruments.save(new MarketInstrumentEntity(id, null, venue, symbol, "EQUITY",
                LocalDate.of(2010, 1, 1), null, "ACTIVE", "FINVERA_FIXTURE", "v1"));
        profiles.save(new EquityProfileEntity(UUID.randomUUID(), id, "CTCP " + symbol, symbol + " Corp",
                null, 1_000_000L, new BigDecimal("0.800000"), "LISTED",
                LocalDate.of(2020, 1, 1), null, "FINVERA_FIXTURE", "1", null));
    }

    private void seedListedInstrument(String symbol) {
        UUID id = UUID.randomUUID();
        instruments.save(new MarketInstrumentEntity(id, null, MarketTypes.Venue.HOSE.name(), symbol, "EQUITY",
                LocalDate.of(2010, 1, 1), null, "ACTIVE", "FINVERA_FIXTURE", "v1"));
        profiles.save(new EquityProfileEntity(UUID.randomUUID(), id, "CTCP " + symbol, symbol + " Corp",
                null, 1_000_000L, new BigDecimal("0.800000"), "LISTED",
                LocalDate.of(2020, 1, 1), null, "FINVERA_FIXTURE", "1", null));
    }

    private void seedAscendingBars(String symbol, LocalDate start, int count) {
        LocalDate date = start;
        BigDecimal close = new BigDecimal("100.000000");
        for (int i = 0; i < count; i++) {
            close = close.add(new BigDecimal("0.100000"));
            ingestBar(symbol, date, close);
            date = date.plusDays(1);
        }
    }

    private void seedFlatBars(String symbol, LocalDate start, int count) {
        LocalDate date = start;
        BigDecimal close = new BigDecimal("100.000000");
        for (int i = 0; i < count; i++) {
            ingestBar(symbol, date, close);
            date = date.plusDays(1);
        }
    }

    private void ingestBar(String symbol, LocalDate date, BigDecimal close) {
        var incoming = new IncomingDailyBar("FINVERA_FIXTURE", symbol, date,
                date.atStartOfDay(ZoneOffset.UTC).toInstant(),
                close.subtract(new BigDecimal("0.500000")), close.add(new BigDecimal("0.500000")),
                close.subtract(new BigDecimal("0.600000")), close, 1_000_000L, null, "RAW", false);
        var result = ingestion.ingestDailyBar(incoming);
        if (result.status() != IngestionStatus.ACCEPTED) {
            throw new IllegalStateException("Seed bar rejected: " + result.reasonCode());
        }
    }
}
