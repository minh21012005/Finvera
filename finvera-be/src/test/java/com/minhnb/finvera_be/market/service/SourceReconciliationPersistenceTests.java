package com.minhnb.finvera_be.market.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.reconciliation.SourceReconciliationPolicy;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.entity.MarketObservationEntity;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.market.repository.MarketObservationRepository;
import com.minhnb.finvera_be.market.repository.SourceReconciliationAuditRepository;
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
class SourceReconciliationPersistenceTests {
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

    @Autowired private SourceReconciliationService service;
    @Autowired private SourceReconciliationAuditRepository audits;
    @Autowired private MarketInstrumentRepository instruments;
    @Autowired private MarketObservationRepository observations;

    @Test
    void storesOneImmutableAuditForAConflictingPairAndReusesItOnReplay() {
        var inputs = persistedInputs();
        var command = command(inputs, raw("101.500000"), raw("101.500001"));

        var first = service.reconcile(command);
        var replay = service.reconcile(command);

        assertThat(first.decision()).isEqualTo(SourceReconciliationPolicy.Decision.SOURCE_CONFLICT);
        assertThat(first.auditId()).contains(replay.auditId().orElseThrow());
        var audit = audits.findById(first.auditId().orElseThrow()).orElseThrow();
        assertThat(audit.getDecision()).isEqualTo("SOURCE_CONFLICT");
        assertThat(audit.getPolicyVersion()).isEqualTo(SourceReconciliationPolicy.VERSION);
        assertThat(audit.getInstrumentId()).isEqualTo(inputs.instrumentId());
    }

    @Test
    void storesNonComparableAdjustmentStatusWithoutSelectingOrAveragingASource() {
        var inputs = persistedInputs();

        var result = service.reconcile(command(inputs, raw("101.500000"), adjusted("101.500000")));

        assertThat(result.decision()).isEqualTo(SourceReconciliationPolicy.Decision.NON_COMPARABLE);
        var audit = audits.findById(result.auditId().orElseThrow()).orElseThrow();
        assertThat(audit.getDecision()).isEqualTo("NON_COMPARABLE");
        assertThat(audit.getTcbsIngestionRecordId()).isEqualTo(inputs.tcbsObservationId());
        assertThat(audit.getVnstockIngestionRecordId()).isEqualTo(inputs.vnstockObservationId());
    }

    private Inputs persistedInputs() {
        LocalDate date = LocalDate.of(2026, 8, 14);
        Instant observedAt = Instant.parse("2026-08-14T08:00:00Z");
        UUID instrumentId = UUID.randomUUID();
        String symbol = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        instruments.save(new MarketInstrumentEntity(instrumentId, null, "HOSE", symbol, "COMMON_EQUITY",
                LocalDate.of(2006, 12, 13), null, "ACTIVE", "TEST", "test-v1"));
        UUID tcbsId = UUID.randomUUID();
        UUID vnstockId = UUID.randomUUID();
        observations.save(new MarketObservationEntity(tcbsId, "TCBS", "EQUITY_DAILY_HISTORY", "HOSE:" + symbol, date,
                observedAt, observedAt, observedAt, "tcbs-1", "a".repeat(64), "ACCEPTED", null, null));
        observations.save(new MarketObservationEntity(vnstockId, "VNSTOCK_KBS", "EQUITY_DAILY_HISTORY", "HOSE:" + symbol, date,
                observedAt, observedAt, observedAt, "vnstock-1", "b".repeat(64), "ACCEPTED", null, null));
        return new Inputs(instrumentId, tcbsId, vnstockId, date);
    }

    private static SourceReconciliationService.Command command(Inputs inputs,
            SourceReconciliationPolicy.Fact tcbs, SourceReconciliationPolicy.Fact vnstock) {
        return new SourceReconciliationService.Command(inputs.instrumentId(), inputs.tradingDate(),
                inputs.tcbsObservationId(), inputs.vnstockObservationId(), tcbs, vnstock);
    }

    private static SourceReconciliationPolicy.Fact raw(String close) {
        return new SourceReconciliationPolicy.Fact(new BigDecimal(close), new BigDecimal("100.000000"),
                SourceReconciliationPolicy.AdjustmentStatus.RAW);
    }

    private static SourceReconciliationPolicy.Fact adjusted(String close) {
        return new SourceReconciliationPolicy.Fact(new BigDecimal(close), new BigDecimal("100.000000"),
                SourceReconciliationPolicy.AdjustmentStatus.PROVIDER_ADJUSTED);
    }

    private record Inputs(UUID instrumentId, UUID tcbsObservationId, UUID vnstockObservationId,
                          LocalDate tradingDate) { }
}
