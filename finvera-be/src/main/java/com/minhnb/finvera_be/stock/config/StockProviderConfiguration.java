package com.minhnb.finvera_be.stock.config;

import com.minhnb.finvera_be.market.service.LiveStockQuoteService;
import com.minhnb.finvera_be.stock.provider.StockQuoteProvider;
import com.minhnb.finvera_be.stock.provider.tcbs.TcbsStreamStockQuoteProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StockProviderConfiguration {
    @Bean
    @ConditionalOnProperty(name = "finvera.stock.provider.quote-live-enabled", havingValue = "true")
    @ConditionalOnBean(LiveStockQuoteService.class)
    StockQuoteProvider tcbsStreamStockQuoteProvider(LiveStockQuoteService quotes) {
        return new TcbsStreamStockQuoteProvider(quotes);
    }
}
