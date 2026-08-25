package com.minhnb.finvera_be.shared.maintenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataRetentionCleanupProperties.class)
public class DataRetentionCleanupConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionCleanupConfiguration.class);

    @Bean
    @ConditionalOnProperty(name = "finvera.data-retention.cleanup.enabled", havingValue = "true")
    ApplicationRunner dataRetentionCleanupRunner(
            DataRetentionCleanupService cleanup,
            DataRetentionCleanupProperties properties) {
        return arguments -> {
            var result = cleanup.cleanup(properties);
            log.info("data_retention_cleanup total_deleted={} audits={} strategy_signals={} technical_results={} valuations={} superseded_daily_bars={} regimes={} breadth_snapshots={} live_equity_observations={} rejected_ingestion_records={}",
                    result.totalDeleted(), result.auditsDeleted(), result.strategySignalsDeleted(),
                    result.technicalResultsDeleted(), result.valuationsDeleted(),
                    result.supersededDailyBarsDeleted(), result.regimesDeleted(),
                    result.breadthSnapshotsDeleted(), result.liveEquityObservationsDeleted(),
                    result.rejectedIngestionRecordsDeleted());
        };
    }
}
