package com.minhnb.finvera_be.research.config;

import com.minhnb.finvera_be.research.service.IngestionCallbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ResearchProperties.class)
public class ResearchConfiguration implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(ResearchConfiguration.class);

    private final ResearchProperties properties;
    private final IngestionCallbackService ingestionCallbackService;

    public ResearchConfiguration(ResearchProperties properties, IngestionCallbackService ingestionCallbackService) {
        this.properties = properties;
        this.ingestionCallbackService = ingestionCallbackService;
    }

    /**
     * Periodically reaps research items left stuck in PENDING/PROCESSING past
     * {@code finvera.research.ingestion-timeout} (lost callback, finvera-ai crash), so an owner never
     * sees an item stuck forever (FR-003/FR-004). The check interval is configurable via
     * {@code finvera.research.ingestion-timeout-check-interval} rather than hardcoded here.
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(
                ingestionCallbackService::checkAndFailStuckProcessingItems,
                properties.ingestionTimeoutCheckInterval());
    }

}
