package com.minhnb.finvera_be.market.config;

import com.minhnb.finvera_be.market.service.FixtureRuntimeBootstrapService;
import com.minhnb.finvera_be.market.service.MarketImportPackageParser;
import com.minhnb.finvera_be.market.service.MarketImportService;
import java.nio.file.Path;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MarketFreshnessProperties.class)
public class MarketConfiguration {
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
