package com.minhnb.finvera_be.shared.maintenance;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "finvera.data-retention.cleanup")
public record DataRetentionCleanupProperties(
        @Min(0) int auditRetentionDays,
        @Min(0) int liveRetentionDays,
        @Min(1) int eodCalculationRetentionDays,
        @Min(0) int revisionRetentionDays) {
}
