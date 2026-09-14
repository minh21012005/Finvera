package com.minhnb.finvera_be.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Assumptions;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Costs;
import com.minhnb.finvera_be.backtest.entity.BacktestEquityPointEntity;
import com.minhnb.finvera_be.backtest.entity.BacktestRunEntity;
import com.minhnb.finvera_be.backtest.repository.BacktestEquityPointRepository;
import com.minhnb.finvera_be.backtest.repository.BacktestRunRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class BacktestRepositoryIntegrationTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    static final UUID OWNER = UUID.randomUUID();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("finvera.security.owner.id", () -> OWNER);
        registry.add("finvera.security.owner.username", () -> "owner-" + UUID.randomUUID());
        registry.add("finvera.security.owner.password-hash",
                () -> new BCryptPasswordEncoder(4).encode(UUID.randomUUID().toString()));
        registry.add("finvera.backtest.worker.enabled", () -> false);
    }

    @Autowired BacktestRunRepository runs;
    @Autowired BacktestEquityPointRepository equity;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void simultaneousClaimsAllowExactlyOneWorker() throws Exception {
        var run = runs.saveAndFlush(run("claim-" + UUID.randomUUID()));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> claimAfterBarrier(run.getId(), ready, start));
            var second = executor.submit(() -> claimAfterBarrier(run.getId(), ready, start));
            ready.await();
            start.countDown();

            assertThat(first.get() + second.get()).isEqualTo(1);
        }
    }

    @Test
    void heartbeatUpdatesOnlyRunningRun() {
        var run = runs.saveAndFlush(run("heartbeat-" + UUID.randomUUID()));
        var claimedAt = Instant.parse("2026-09-14T01:00:00Z");
        var heartbeatAt = claimedAt.plusSeconds(30);
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> runs.claim(run.getId(), claimedAt));

        var updated = new TransactionTemplate(transactionManager).execute(ignored -> runs.heartbeat(run.getId(), heartbeatAt));

        assertThat(updated).isOne();
        assertThat(runs.findById(run.getId()).orElseThrow().getHeartbeatAt()).isEqualTo(heartbeatAt);
    }

    @Test
    void childWritesRollBackAtomicallyWhenExecutionFails() {
        var run = runs.saveAndFlush(run("rollback-" + UUID.randomUUID()));
        long before = equity.count();
        var tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> {
            equity.saveAndFlush(new BacktestEquityPointEntity(UUID.randomUUID(), run.getId(),
                    LocalDate.of(2026, 9, 11), bd("100000000"), BigDecimal.ZERO, bd("100000000"),
                    (short) 0, null, UUID.randomUUID()));
            throw new IllegalStateException("FORCED_EXECUTION_FAILURE");
        })).hasMessage("FORCED_EXECUTION_FAILURE");

        assertThat(equity.count()).isEqualTo(before);
    }

    private int claimAfterBarrier(UUID id, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return new TransactionTemplate(transactionManager).execute(ignored -> runs.claim(id, Instant.now()));
    }

    private static BacktestRunEntity run(String key) {
        var costs = new Costs(bd("0.001"), bd("0.001"), bd("0.001"), bd("0.001"), bd("0.001"), false);
        return new BacktestRunEntity(UUID.randomUUID(), OWNER, key, "RSI_BASED", "FPT", UUID.randomUUID(),
                "HOSE", LocalDate.of(2025, 1, 2), LocalDate.of(2025, 2, 3),
                new Assumptions(bd("100000000"), bd("0.01"), bd("0.04"), costs),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.now());
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
