package com.minhnb.finvera_be.market.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.repository.EquityPriceObservationRepository;
import com.minhnb.finvera_be.market.repository.MarketImportBatchRepository;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.market.repository.MarketObservationRepository;
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
class MarketImportPersistenceTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("finvera.market.import.enabled", () -> "true");
        registry.add("finvera.security.owner.id", UUID::randomUUID);
        registry.add("finvera.security.owner.username", () -> "owner-" + UUID.randomUUID());
        registry.add("finvera.security.owner.password-hash",
                () -> new BCryptPasswordEncoder(4).encode(UUID.randomUUID().toString()));
    }

    @Autowired private MarketImportService service;
    @Autowired private MarketImportBatchRepository batches;
    @Autowired private MarketInstrumentRepository instruments;
    @Autowired private MarketObservationRepository observations;
    @Autowired private EquityPriceObservationRepository prices;

    @Test
    void importsOneCanonicalPackageIntoPostgresAndReplayingItIsIdempotent() {
        var input = packageInput("canonical-package-persistence-v1");

        var first = service.importPackage(input);
        var replay = service.importPackage(input);

        assertThat(first.status()).isEqualTo(MarketImportService.Status.APPLIED);
        assertThat(replay.status()).isEqualTo(MarketImportService.Status.ALREADY_APPLIED);
        assertThat(batches.count()).isEqualTo(1);
        assertThat(instruments.count()).isEqualTo(1);
        assertThat(observations.count()).isEqualTo(1);
        assertThat(prices.count()).isEqualTo(1);
    }

    private static MarketImportService.PackageInput packageInput(String payload) {
        Instant observedAt = Instant.parse("2026-08-17T03:00:00Z");
        return new MarketImportService.PackageInput(MarketImportService.CONTRACT_VERSION, "finvera-vnstock-exporter",
                "0.1.0", "VNSTOCK_KBS", MarketImportService.sha256(payload), payload, observedAt,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 15),
                List.of(new MarketImportService.EquityHistoryRecord("HOSE", "FPT", null,
                        LocalDate.of(2006, 12, 13), "ACTIVE", LocalDate.of(2026, 8, 15), observedAt,
                        "101.500000", "RAW", null, "record-persistence-1")));
    }
}
