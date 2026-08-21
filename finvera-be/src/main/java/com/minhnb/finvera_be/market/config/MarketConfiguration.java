package com.minhnb.finvera_be.market.config;

import com.minhnb.finvera_be.market.provider.MarketDataProvider;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsHttpRestClient;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsHttpSessionState;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsMarketDataProvider;
import com.minhnb.finvera_be.market.service.FixtureRuntimeBootstrapService;
import com.minhnb.finvera_be.market.service.MarketImportPackageParser;
import com.minhnb.finvera_be.market.service.MarketImportService;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({MarketFreshnessProperties.class, TcbsProviderProperties.class})
public class MarketConfiguration {

    // Live TCBS wiring: three collaborating beans, all conditional on live mode. Kept as plain,
    // framework-agnostic classes (see TcbsMarketDataProviderTests, which constructs the provider
    // with `new` and mocked ports) and assembled here rather than annotated directly, so the
    // adapter and its tests stay decoupled from Spring.
    @Bean
    @ConditionalOnProperty(name = "finvera.market.provider.mode", havingValue = "live")
    TcbsHttpSessionState tcbsHttpSessionState(TcbsProviderProperties properties, Clock clock) {
        return new TcbsHttpSessionState(properties.baseUrl(), properties.apiKey(), clock);
    }

    @Bean
    @ConditionalOnProperty(name = "finvera.market.provider.mode", havingValue = "live")
    TcbsHttpRestClient tcbsHttpRestClient(TcbsProviderProperties properties, TcbsHttpSessionState sessionState) {
        return new TcbsHttpRestClient(properties.baseUrl(), sessionState);
    }

    @Bean
    @ConditionalOnProperty(name = "finvera.market.provider.mode", havingValue = "live")
    MarketDataProvider tcbsMarketDataProvider(TcbsHttpRestClient restClient, TcbsHttpSessionState sessionState) {
        return new TcbsMarketDataProvider(restClient, sessionState);
    }
    @Bean
    @ConditionalOnProperty(name = "finvera.market.fixture.bootstrap-enabled", havingValue = "true")
    @ConditionalOnProperty(name = "finvera.market.provider.mode", havingValue = "fixture")
    ApplicationRunner fixtureRuntimeBootstrap(FixtureRuntimeBootstrapService bootstrap) {
        return arguments -> bootstrap.bootstrap();
    }

    @Bean
    @ConditionalOnProperty(name = "finvera.market.import.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "finvera.market.import.package-path")
    ApplicationRunner localHistoricalImport(@Value("${finvera.market.import.package-path}") String packagePath,
            MarketImportPackageParser parser, MarketImportService importer) {
        return arguments -> {
            if (!packagePath.isBlank()) importer.importPackage(parser.parse(Path.of(packagePath)));
        };
    }
}
