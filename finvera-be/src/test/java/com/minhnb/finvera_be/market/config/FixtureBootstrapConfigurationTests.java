package com.minhnb.finvera_be.market.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.minhnb.finvera_be.market.service.FixtureRuntimeBootstrapService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FixtureBootstrapConfigurationTests {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(MarketConfiguration.class)
            .withPropertyValues("finvera.market.freshness.index-contracted-delay=PT15M",
                    "finvera.market.provider.mode=fixture")
            .withBean(FixtureRuntimeBootstrapService.class, () -> mock(FixtureRuntimeBootstrapService.class));

    @Test void bootstrapRunnerIsAbsentUnlessExplicitlyEnabled() {
        context.run(result -> assertThat(result).doesNotHaveBean(ApplicationRunner.class));
    }

    @Test void bootstrapRunnerExistsWhenExplicitlyEnabled() {
        context.withPropertyValues("finvera.market.fixture.bootstrap-enabled=true")
                .run(result -> assertThat(result).hasSingleBean(ApplicationRunner.class));
    }

    @Test void bootstrapRunnerIsAbsentForNonFixtureProviderMode() {
        context.withPropertyValues("finvera.market.fixture.bootstrap-enabled=true",
                        "finvera.market.provider.mode=tcbs-iflash-private")
                .run(result -> assertThat(result).doesNotHaveBean(ApplicationRunner.class));
    }
}
