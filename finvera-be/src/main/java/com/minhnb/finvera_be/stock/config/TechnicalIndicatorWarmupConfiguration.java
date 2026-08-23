package com.minhnb.finvera_be.stock.config;

import com.minhnb.finvera_be.stock.service.TechnicalIndicatorWarmupService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Owner-triggered runner for {@link TechnicalIndicatorWarmupService}. Same one-time-then-disable
 * convention as {@code StockImportConfiguration}'s import runners: enable, restart once, disable.
 */
@Configuration(proxyBeanMethods = false)
public class TechnicalIndicatorWarmupConfiguration {

    @Bean
    @ConditionalOnProperty(name = "finvera.stock.technical.warmup.enabled", havingValue = "true")
    ApplicationRunner technicalIndicatorWarmupRunner(TechnicalIndicatorWarmupService warmup) {
        return arguments -> warmup.warmUp();
    }
}
