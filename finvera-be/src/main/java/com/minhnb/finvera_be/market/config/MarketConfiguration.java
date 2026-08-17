package com.minhnb.finvera_be.market.config;

import com.minhnb.finvera_be.market.service.FixtureRuntimeBootstrapService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MarketFreshnessProperties.class)
public class MarketConfiguration {
    @Bean
    @ConditionalOnProperty(name = "finvera.market.fixture.bootstrap-enabled", havingValue = "true")
    @ConditionalOnProperty(name = "finvera.market.provider.mode", havingValue = "fixture")
    ApplicationRunner fixtureRuntimeBootstrap(FixtureRuntimeBootstrapService bootstrap) {
        return arguments -> bootstrap.bootstrap();
    }
}
