package com.minhnb.finvera_be.shared.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DataRetentionCleanupConfigurationTests {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(DataRetentionCleanupConfiguration.class)
            .withBean(DataRetentionCleanupService.class, () -> mock(DataRetentionCleanupService.class))
            .withPropertyValues(
                    "finvera.data-retention.cleanup.audit-retention-days=30",
                    "finvera.data-retention.cleanup.live-retention-days=7",
                    "finvera.data-retention.cleanup.eod-calculation-retention-days=252",
                    "finvera.data-retention.cleanup.revision-retention-days=30");

    @Test
    void cleanupRunnerIsAbsentUnlessExplicitlyEnabled() {
        context.run(result -> assertThat(result).doesNotHaveBean(ApplicationRunner.class));
    }

    @Test
    void cleanupRunnerExistsWhenExplicitlyEnabled() {
        context.withPropertyValues("finvera.data-retention.cleanup.enabled=true")
                .run(result -> assertThat(result).hasSingleBean(ApplicationRunner.class));
    }
}
