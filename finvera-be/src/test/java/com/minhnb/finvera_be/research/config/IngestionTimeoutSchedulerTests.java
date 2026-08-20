package com.minhnb.finvera_be.research.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.research.domain.DocumentType;
import com.minhnb.finvera_be.research.domain.IngestionStatus;
import com.minhnb.finvera_be.research.entity.ResearchDocumentEntity;
import com.minhnb.finvera_be.research.repository.ResearchDocumentRepository;
import java.time.Duration;
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

/**
 * Proves the ingestion-timeout reaper ({@link com.minhnb.finvera_be.research.service.IngestionCallbackService
 * #checkAndFailStuckProcessingItems()}) is actually invoked by the Spring scheduler in a running application
 * ({@link ResearchConfiguration}), not just callable directly (that path is already covered by
 * {@code IngestionCallbackServiceTests}). The check interval is overridden to a short value so the test does
 * not need to wait out the real timeout.
 */
@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class IngestionTimeoutSchedulerTests {

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
        registry.add("finvera.research.ingestion-timeout-check-interval", () -> "200ms");
    }

    @Autowired
    private ResearchDocumentRepository documentRepository;

    @Test
    void schedulerFiresAndFailsAStuckDocumentWithoutAnyDirectMethodCall() throws InterruptedException {
        UUID docId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant longPastTimeout = Instant.now().minus(Duration.ofHours(1));

        ResearchDocumentEntity stuckDoc = new ResearchDocumentEntity(
                docId, ownerId, null, "Stuck By Scheduler", DocumentType.ANNUAL_REPORT, (short) 2026, null,
                "Source", LocalDate.now(), null, null, null, null, IngestionStatus.PROCESSING, null,
                longPastTimeout, null, "idem-scheduler-" + docId);
        documentRepository.saveAndFlush(stuckDoc);

        // Bounded poll: the scheduler (fixed-delay 200ms) must flip this row to FAILED on its own.
        // We never call checkAndFailStuckProcessingItems() ourselves here.
        long deadline = System.currentTimeMillis() + 3000;
        IngestionStatus observed = null;
        while (System.currentTimeMillis() < deadline) {
            observed = documentRepository.findById(docId).orElseThrow().getIngestionStatus();
            if (observed == IngestionStatus.FAILED) {
                break;
            }
            Thread.sleep(100);
        }

        assertThat(observed).isEqualTo(IngestionStatus.FAILED);
        var reloaded = documentRepository.findById(docId).orElseThrow();
        assertThat(reloaded.getIngestionFailureReason()).isEqualTo("PROCESSING_TIMEOUT");
    }
}
