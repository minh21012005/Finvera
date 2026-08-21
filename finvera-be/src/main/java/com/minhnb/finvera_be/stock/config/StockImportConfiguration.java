package com.minhnb.finvera_be.stock.config;

import com.minhnb.finvera_be.stock.service.FundamentalReportImportPackageParser;
import com.minhnb.finvera_be.stock.service.FundamentalReportImportService;
import com.minhnb.finvera_be.stock.service.SectorReferenceImportPackageParser;
import com.minhnb.finvera_be.stock.service.SectorReferenceImportService;
import com.minhnb.finvera_be.stock.service.StockHistoryImportPackageParser;
import com.minhnb.finvera_be.stock.service.StockHistoryImportService;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Owner-triggered import runners for Feature 002's Vnstock-sourced datasets (daily bars,
 * fundamentals, sector reference). Mirrors {@code market.config.MarketConfiguration}'s
 * import-runner pattern exactly: each dataset has its own {@code enabled}/{@code package-path}
 * pair so an owner imports one dataset at a time and nothing runs by default.
 */
@Configuration(proxyBeanMethods = false)
public class StockImportConfiguration {

    @Bean
    @ConditionalOnProperty(name = "finvera.stock.import.daily-bar.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "finvera.stock.import.daily-bar.package-path")
    ApplicationRunner stockDailyBarImport(
            @Value("${finvera.stock.import.daily-bar.package-path}") String packagePath,
            StockHistoryImportPackageParser parser, StockHistoryImportService importer) {
        return arguments -> {
            if (!packagePath.isBlank()) {
                importer.importPackage(parser.parse(Path.of(packagePath)));
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "finvera.stock.import.fundamentals.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "finvera.stock.import.fundamentals.package-path")
    ApplicationRunner stockFundamentalsImport(
            @Value("${finvera.stock.import.fundamentals.package-path}") String packagePath,
            FundamentalReportImportPackageParser parser, FundamentalReportImportService importer) {
        return arguments -> {
            if (!packagePath.isBlank()) {
                importer.importPackage(parser.parse(Path.of(packagePath)));
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "finvera.stock.import.sector-reference.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "finvera.stock.import.sector-reference.package-path")
    ApplicationRunner stockSectorReferenceImport(
            @Value("${finvera.stock.import.sector-reference.package-path}") String packagePath,
            SectorReferenceImportPackageParser parser, SectorReferenceImportService importer) {
        return arguments -> {
            if (!packagePath.isBlank()) {
                importer.importPackage(parser.parse(Path.of(packagePath)));
            }
        };
    }
}
