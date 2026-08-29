package com.minhnb.finvera_be.market.config;

import com.minhnb.finvera_be.market.service.FixtureRuntimeBootstrapService;
import com.minhnb.finvera_be.market.service.MarketImportPackageParser;
import com.minhnb.finvera_be.market.service.MarketImportService;
import com.minhnb.finvera_be.market.service.MarketInstrumentReferenceImportPackageParser;
import com.minhnb.finvera_be.market.service.MarketInstrumentReferenceImportService;
import com.minhnb.finvera_be.market.service.BreadthService;
import com.minhnb.finvera_be.market.service.MarketIngestionService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.HistoricalMarketBreadthReconciliationService;
import com.minhnb.finvera_be.market.service.TcbsLiveMarketIngestionService;
import com.minhnb.finvera_be.market.service.LiveMarketRegimeReconciliationService;
import com.minhnb.finvera_be.market.service.TcbsLiveEquityQuoteService;
import com.minhnb.finvera_be.market.service.IngestionRecordService;
import com.minhnb.finvera_be.market.repository.EquityPriceObservationRepository;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsHttpSessionState;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsThesisWebSocketClient;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({MarketFreshnessProperties.class, TcbsThesisProperties.class})
public class MarketConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MarketConfiguration.class);

    @Bean
    @ConditionalOnProperty(name = "finvera.market.live-overlay.tcbs.enabled", havingValue = "true")
    TcbsHttpSessionState tcbsHttpSessionState(TcbsThesisProperties properties, Clock clock) {
        return new TcbsHttpSessionState(properties.baseUrl(), properties.apiKey(), clock);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "finvera.market.live-overlay.tcbs.enabled", havingValue = "true")
    TcbsThesisWebSocketClient tcbsThesisWebSocketClient(TcbsThesisProperties properties,
            TcbsHttpSessionState session, Clock clock) {
        return new TcbsThesisWebSocketClient(URI.create(properties.websocketUrl()), session,
                properties.heartbeatInterval(), properties.reconnectMaxDelay(), properties.maxDynamicSymbols(), clock);
    }

    @Bean
    @ConditionalOnProperty(name = "finvera.market.live-overlay.tcbs.enabled", havingValue = "true")
    TcbsLiveMarketIngestionService tcbsLiveMarketIngestionService(MarketIngestionService ingestion,
            BreadthService breadth, MarketReferenceDataService referenceData,
            LiveMarketRegimeReconciliationService regimeReconciliation) {
        return new TcbsLiveMarketIngestionService(ingestion, breadth, referenceData, regimeReconciliation);
    }

    @Bean
    @ConditionalOnProperty(name = "finvera.market.live-overlay.tcbs.enabled", havingValue = "true")
    AutoCloseable tcbsThesisObserverRegistration(TcbsThesisWebSocketClient client,
            TcbsLiveMarketIngestionService ingestion) {
        return client.observe(ingestion);
    }

    @Bean
    @ConditionalOnProperty(name = "finvera.market.live-overlay.tcbs.enabled", havingValue = "true")
    TcbsLiveEquityQuoteService tcbsLiveEquityQuoteService(MarketReferenceDataService referenceData,
            IngestionRecordService records, EquityPriceObservationRepository prices,
            TcbsThesisWebSocketClient client, Optional<TcbsHttpSessionState> sessionState, Clock clock) {
        return new TcbsLiveEquityQuoteService(referenceData, records, prices, client, sessionState, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "finvera.market.live-overlay.tcbs.enabled", havingValue = "true")
    AutoCloseable tcbsThesisEquityObserverRegistration(TcbsThesisWebSocketClient client,
            TcbsLiveEquityQuoteService quotes) {
        return client.observe(quotes);
    }

    /**
     * Startup read-repair for late-arriving VN-Index history. Never runs alongside
     * the fixture runtime bootstrap: that dataset is complete by construction, and
     * repairing it only manufactures a withheld LIVE-basis assessment (the fixture
     * history is far shorter than the 220-session trend window) whose later
     * calculatedAt then shadows the bootstrap's published assessment in the
     * latest-per-trading-date overview read.
     */
    @Bean
    @ConditionalOnBean({BreadthService.class, LiveMarketRegimeReconciliationService.class})
    @ConditionalOnProperty(name = "finvera.market.fixture.bootstrap-enabled", havingValue = "false",
            matchIfMissing = true)
    ApplicationRunner marketRegimeReadRepair(BreadthService breadth,
            LiveMarketRegimeReconciliationService regimeReconciliation) {
        return arguments -> breadth.latest().ifPresent(snapshot -> {
            if ("EOD".equals(snapshot.calculationBasis())) {
                regimeReconciliation.reconcileEndOfDayIfMissingOrOlder(snapshot.tradingDate(), snapshot);
            } else {
                regimeReconciliation.reconcileIfMissingOrOlder(snapshot.tradingDate(), snapshot);
            }
        });
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
            if (packagePath.isBlank()) return;
            var result = importer.importPackage(parser.parse(Path.of(packagePath)));
            log.info("market_import status={} package_sha256={}", result.status(), result.packageSha256());
        };
    }

    @Bean
    @ConditionalOnProperty(name = "finvera.market.eod-reconciliation.enabled", havingValue = "true")
    ApplicationRunner historicalMarketReconciliation(HistoricalMarketBreadthReconciliationService reconciliation) {
        return arguments -> {
            var result = reconciliation.reconcileLatestCompletedSession();
            log.info("market_eod_reconciliation status={} trading_date={} breadth_snapshot_id={} eligible={} advancing={} declining={} unchanged={} unclassified={} reason={}",
                    result.status(), result.tradingDate(), result.breadthSnapshotId(), result.eligible(),
                    result.advancing(), result.declining(), result.unchanged(), result.unclassified(),
                    result.reasonCode());
        };
    }

    // Must run before any daily-bar/fundamentals bulk import: those reject a symbol with
    // UNKNOWN_INSTRUMENT unless it already has an active market_instrument row (see
    // DefaultMarketReferenceDataService.findActiveInstrumentBySymbol).
    @Bean
    @ConditionalOnProperty(name = "finvera.market.import.instrument-reference.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "finvera.market.import.instrument-reference.package-path")
    ApplicationRunner localInstrumentReferenceImport(
            @Value("${finvera.market.import.instrument-reference.package-path}") String packagePath,
            MarketInstrumentReferenceImportPackageParser parser, MarketInstrumentReferenceImportService importer) {
        return arguments -> {
            if (packagePath.isBlank()) return;
            var result = importer.importPackage(parser.parse(Path.of(packagePath)));
            log.info("instrument_reference_import status={} registered={} skipped={}",
                    result.status(), result.registered(), result.skipped());
        };
    }
}
