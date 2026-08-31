package com.minhnb.finvera_be.stock.config;

import com.minhnb.finvera_be.stock.service.FundamentalSourceRetirementService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Owner-triggered, run-once runner (same convention as the import/warmup runners):
 * {@code FINVERA_STOCK_FUNDAMENTALS_RETIRE_ENABLED=true} with
 * {@code FINVERA_STOCK_FUNDAMENTALS_RETIRE_SOURCE=VNSTOCK_KBS}. Feature 018.
 */
@Configuration(proxyBeanMethods = false)
public class FundamentalSourceRetirementConfiguration {

    @Bean
    @ConditionalOnProperty(name = "finvera.stock.fundamentals.retire.enabled", havingValue = "true")
    ApplicationRunner fundamentalSourceRetirementRunner(
            FundamentalSourceRetirementService retirement,
            @Value("${finvera.stock.fundamentals.retire.source:}") String source) {
        return arguments -> {
            if (source == null || source.isBlank()) {
                throw new IllegalStateException("finvera.stock.fundamentals.retire.source must name the source to retire");
            }
            retirement.retire(source.trim());
        };
    }
}
