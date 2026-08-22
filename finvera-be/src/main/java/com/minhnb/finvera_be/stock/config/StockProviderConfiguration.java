package com.minhnb.finvera_be.stock.config;

import com.minhnb.finvera_be.market.provider.tcbs.TcbsHttpRestClient;
import com.minhnb.finvera_be.stock.provider.tcbs.TcbsStockQuoteProvider;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Live per-instrument quote provider (research.md R-012 gate G-03, closed 2026-08-22). Reuses
 * Feature 001's {@link TcbsHttpRestClient} bean (per R-001: no second live provider) rather than
 * building a separate HTTP/session stack, so this bean can only exist when that one does —
 * {@code finvera.market.provider.mode=live} (Feature 001 Phase 9) as well as this feature's own
 * {@code finvera.stock.provider.quote-live-enabled} flag.
 */
@Configuration(proxyBeanMethods = false)
public class StockProviderConfiguration {

    @Bean
    @ConditionalOnProperty(name = "finvera.stock.provider.quote-live-enabled", havingValue = "true")
    @ConditionalOnBean(TcbsHttpRestClient.class)
    TcbsStockQuoteProvider tcbsStockQuoteProvider(TcbsHttpRestClient restClient, Clock clock) {
        return new TcbsStockQuoteProvider(restClient, clock);
    }
}
