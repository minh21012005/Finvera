package com.minhnb.finvera_be.market.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.entity.MarketIndexSnapshotEntity;
import com.minhnb.finvera_be.market.entity.MarketObservationEntity;
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

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class MarketRepositoryTests {

    private static final UUID VN_INDEX_ID = UUID.fromString("00000000-0000-0000-0001-000000000001");
    private static final LocalDate TRADING_DATE = LocalDate.of(2026, 8, 17);

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

    @Autowired
    MarketObservationRepository observations;

    @Autowired
    MarketIndexSnapshotRepository snapshots;

    @Autowired
    MarketOverviewRepository overviewRepository;

    @Test
    void roundTripsUtcInstantsAndExactDecimals() {
        UUID ingestionId = UUID.randomUUID();
        Instant observedAt = Instant.parse("2026-08-17T03:00:00Z");
        observations.save(observation(ingestionId, observedAt, "a".repeat(64), null));
        snapshots.save(snapshot(UUID.randomUUID(), ingestionId, observedAt,
                new BigDecimal("1280.250000"), 1, null));

        var loaded = snapshots
                .findFirstByIndexIdAndTradingDateOrderByObservedAtDescRevisionDesc(VN_INDEX_ID, TRADING_DATE)
                .orElseThrow();

        assertThat(loaded.getObservedAt()).isEqualTo(observedAt);
        assertThat(loaded.getIndexLevel()).isEqualByComparingTo("1280.250000");
        assertThat(loaded.getMatchedValueVnd()).isEqualByComparingTo("11250000000000.0000");
    }

    @Test
    void correctionAppendsANewRevisionAndKeepsTheSupersededRow() {
        long snapshotsBeforeCorrection = snapshots.count();
        Instant observedAt = Instant.parse("2026-08-17T03:01:00Z");
        UUID firstIngestion = UUID.randomUUID();
        UUID firstSnapshot = UUID.randomUUID();
        observations.save(observation(firstIngestion, observedAt, "b".repeat(64), null));
        snapshots.save(snapshot(firstSnapshot, firstIngestion, observedAt,
                new BigDecimal("1280.250000"), 1, null));

        UUID correctionIngestion = UUID.randomUUID();
        observations.save(observation(correctionIngestion, observedAt, "c".repeat(64), firstIngestion));
        snapshots.save(snapshot(UUID.randomUUID(), correctionIngestion, observedAt,
                new BigDecimal("1280.300000"), 2, firstSnapshot));

        var latest = snapshots
                .findFirstByIndexIdAndTradingDateOrderByObservedAtDescRevisionDesc(VN_INDEX_ID, TRADING_DATE)
                .orElseThrow();
        assertThat(latest.getRevision()).isEqualTo(2);
        assertThat(latest.getIndexLevel()).isEqualByComparingTo("1280.300000");
        assertThat(latest.getSupersedesId()).isEqualTo(firstSnapshot);
        assertThat(snapshots.count()).isEqualTo(snapshotsBeforeCorrection + 2);
    }

    @Test
    void readsOneLatestTradingDateAndLatestRevisionForEachSupportedIndex() {
        LocalDate coherentDate = TRADING_DATE.plusDays(1);
        Instant observedAt = Instant.parse("2026-08-18T03:00:00Z");
        UUID vnIndexFirst = saveSnapshot(VN_INDEX_ID, "VN_INDEX", coherentDate, observedAt,
                new BigDecimal("1300.000000"), 1, null);
        saveSnapshot(UUID.fromString("00000000-0000-0000-0001-000000000002"), "VN30", coherentDate,
                observedAt, new BigDecimal("1400.000000"), 1, null);
        saveSnapshot(UUID.fromString("00000000-0000-0000-0001-000000000003"), "HNX_INDEX", coherentDate,
                observedAt, new BigDecimal("230.000000"), 1, null);
        saveSnapshot(UUID.fromString("00000000-0000-0000-0001-000000000004"), "UPCOM_INDEX", coherentDate,
                observedAt, new BigDecimal("105.000000"), 1, null);
        saveSnapshot(VN_INDEX_ID, "VN_INDEX", coherentDate, observedAt,
                new BigDecimal("1301.000000"), 2, vnIndexFirst);

        var latest = overviewRepository.findLatestAcceptedIndexBoundary();

        assertThat(latest).hasSize(4);
        assertThat(latest).extracting(MarketOverviewRepository.LatestIndexSnapshot::getIndexCode)
                .containsExactly("VN_INDEX", "VN30", "HNX_INDEX", "UPCOM_INDEX");
        assertThat(latest).allMatch(row -> coherentDate.equals(row.getTradingDate()));
        assertThat(latest.getFirst().getRevision()).isEqualTo(2);
        assertThat(latest.getFirst().getIndexLevel()).isEqualByComparingTo("1301.000000");
    }

    private UUID saveSnapshot(UUID indexId, String code, LocalDate date, Instant observedAt,
            BigDecimal level, int revision, UUID supersedesId) {
        UUID ingestionId = UUID.randomUUID();
        observations.save(new MarketObservationEntity(ingestionId, "FINVERA_FIXTURE", "INDEX", code, date,
                observedAt, observedAt, observedAt.plusSeconds(revision), null,
                String.format("%064x", ingestionId.getLeastSignificantBits() & Long.MAX_VALUE),
                "ACCEPTED", null, null));
        UUID snapshotId = UUID.randomUUID();
        BigDecimal reference = new BigDecimal("1275.000000");
        BigDecimal change = level.subtract(reference);
        snapshots.save(new MarketIndexSnapshotEntity(snapshotId, indexId, ingestionId, date, observedAt,
                observedAt.plusSeconds(revision), "OPEN", level, reference, change,
                change.multiply(new BigDecimal("100")).divide(reference, 6, java.math.RoundingMode.HALF_UP),
                420_000_000L, new BigDecimal("11250000000000.0000"), "FINVERA_FIXTURE", revision,
                supersedesId));
        return snapshotId;
    }

    private static MarketObservationEntity observation(
            UUID id, Instant observedAt, String hash, UUID supersedesId) {
        return new MarketObservationEntity(id, "FINVERA_FIXTURE", "INDEX", "VN_INDEX", TRADING_DATE,
                observedAt, observedAt, observedAt.plusSeconds(1), null, hash,
                "ACCEPTED", null, supersedesId);
    }

    private static MarketIndexSnapshotEntity snapshot(UUID id, UUID ingestionId, Instant observedAt,
            BigDecimal level, int revision, UUID supersedesId) {
        BigDecimal reference = new BigDecimal("1275.000000");
        BigDecimal change = level.subtract(reference);
        return new MarketIndexSnapshotEntity(id, VN_INDEX_ID, ingestionId, TRADING_DATE, observedAt,
                observedAt.plusSeconds(revision), "OPEN", level, reference, change,
                change.multiply(new BigDecimal("100")).divide(reference, 6, java.math.RoundingMode.HALF_UP),
                420_000_000L, new BigDecimal("11250000000000.0000"),
                "FINVERA_FIXTURE", revision, supersedesId);
    }
}
