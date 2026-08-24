package com.minhnb.finvera_be.stock.config;

import com.minhnb.finvera_be.stock.service.ValuationWarmupService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Owner-triggered runner for {@link ValuationWarmupService}. Same one-time-
 * then-disable convention as the import and technical warmup runners.
 */
@Configuration(proxyBeanMethods = false)
public class ValuationWarmupConfiguration {

    @Bean
    @ConditionalOnProperty(name = "finvera.stock.valuation.warmup.enabled", havingValue = "true")
    ApplicationRunner valuationWarmupRunner(ValuationWarmupService warmup) {
        return arguments -> warmup.warmUp();
    }
}
