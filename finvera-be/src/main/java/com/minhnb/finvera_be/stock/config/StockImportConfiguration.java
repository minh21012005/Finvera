package com.minhnb.finvera_be.stock.config;

import com.minhnb.finvera_be.stock.service.EquityProfileImportPackageParser;
import com.minhnb.finvera_be.stock.service.EquityProfileImportService;
import com.minhnb.finvera_be.stock.service.FundamentalReportImportPackageParser;
import com.minhnb.finvera_be.stock.service.FundamentalReportImportService;
import com.minhnb.finvera_be.stock.service.SectorReferenceImportPackageParser;
import com.minhnb.finvera_be.stock.service.SectorReferenceImportService;
import com.minhnb.finvera_be.stock.service.StockHistoryImportPackageParser;
import com.minhnb.finvera_be.stock.service.StockHistoryImportService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * <p>{@code package-path} accepts either one package file or a directory — a directory is scanned
 * for every file matching that dataset's naming convention (e.g. {@code daily-bars-*.json},
 * produced by {@code export_all_symbols.py}'s per-symbol batch export) and each is imported in
 * turn. One bad file is logged and skipped rather than aborting the whole batch, since a
 * multi-hundred-symbol run will always have a handful of structurally unusual symbols.
 */
@Configuration(proxyBeanMethods = false)
public class StockImportConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StockImportConfiguration.class);

    // Must run before sector-reference below: SectorReferenceImportService can only link a
    // symbol's sector onto an equity_profile row that already exists (see its own NO_EQUITY_PROFILE
    // status) -- it deliberately never creates one itself.
    @Bean
    @ConditionalOnProperty(name = "finvera.stock.import.equity-profile.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "finvera.stock.import.equity-profile.package-path")
    ApplicationRunner stockEquityProfileImport(
            @Value("${finvera.stock.import.equity-profile.package-path}") String packagePath,
            EquityProfileImportPackageParser parser, EquityProfileImportService importer) {
        return arguments -> importAll("equity-profile", packagePath, "equity-profile*.json",
                path -> importer.importPackage(parser.parse(path)));
    }

    @Bean
    @ConditionalOnProperty(name = "finvera.stock.import.daily-bar.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "finvera.stock.import.daily-bar.package-path")
    ApplicationRunner stockDailyBarImport(
            @Value("${finvera.stock.import.daily-bar.package-path}") String packagePath,
            StockHistoryImportPackageParser parser, StockHistoryImportService importer) {
        return arguments -> importAll("daily-bar", packagePath, "daily-bars-*.json",
                path -> importer.importPackage(parser.parse(path)));
    }

    @Bean
    @ConditionalOnProperty(name = "finvera.stock.import.fundamentals.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "finvera.stock.import.fundamentals.package-path")
    ApplicationRunner stockFundamentalsImport(
            @Value("${finvera.stock.import.fundamentals.package-path}") String packagePath,
            FundamentalReportImportPackageParser parser, FundamentalReportImportService importer) {
        return arguments -> importAll("fundamentals", packagePath, "fundamentals-*.json",
                path -> importer.importPackage(parser.parse(path)));
    }

    @Bean
    @ConditionalOnProperty(name = "finvera.stock.import.sector-reference.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "finvera.stock.import.sector-reference.package-path")
    ApplicationRunner stockSectorReferenceImport(
            @Value("${finvera.stock.import.sector-reference.package-path}") String packagePath,
            SectorReferenceImportPackageParser parser, SectorReferenceImportService importer) {
        return arguments -> importAll("sector-reference", packagePath, "sector-reference-*.json",
                path -> importer.importPackage(parser.parse(path)));
    }

    /**
     * @param glob only applied when {@code packagePath} is a directory, so a shared output folder
     *             (e.g. {@code export_all_symbols.py}'s, which also holds the checkpoint file and
     *             the other dataset's packages) does not get the wrong files fed to this importer.
     */
    static void importAll(String dataset, String packagePath, String glob, Importer importer) {
        if (packagePath == null || packagePath.isBlank()) {
            return;
        }
        Path path = Path.of(packagePath);
        List<Path> files = resolveFiles(path, glob);
        if (files.isEmpty()) {
            log.warn("stock_import dataset={} path={} matched no files", dataset, packagePath);
            return;
        }
        int succeeded = 0;
        int failed = 0;
        for (Path file : files) {
            try {
                importer.importOne(file);
                succeeded++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("stock_import dataset={} file={} rejected: {}: {}",
                        dataset, file.getFileName(), e.getClass().getSimpleName(), e.getMessage());
            }
        }
        log.info("stock_import dataset={} total={} succeeded={} failed={}",
                dataset, files.size(), succeeded, failed);
    }

    private static List<Path> resolveFiles(Path path, String glob) {
        if (Files.isRegularFile(path)) {
            return List.of(path);
        }
        if (!Files.isDirectory(path)) {
            return List.of();
        }
        try (var stream = Files.newDirectoryStream(path, glob)) {
            List<Path> files = new java.util.ArrayList<>();
            stream.forEach(files::add);
            files.sort(Comparator.comparing(Path::getFileName));
            return files;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to list import directory: " + path, e);
        }
    }

    @FunctionalInterface
    interface Importer {
        void importOne(Path file);
    }
}
