package com.minhnb.finvera_be.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "fundamental_summary_metric")
@IdClass(FundamentalSummaryMetricEntity.Key.class)
public class FundamentalSummaryMetricEntity {
    @Id @Column(name = "summary_id") private UUID summaryId;
    @Id @Column(name = "metric_code") private String metricCode;
    @Column(precision = 28, scale = 6) private BigDecimal value;
    private String applicability;
    @Column(name = "quality_reason") private String qualityReason;

    protected FundamentalSummaryMetricEntity() { }

    public FundamentalSummaryMetricEntity(UUID summaryId, String metricCode, BigDecimal value,
            String applicability, String qualityReason) {
        this.summaryId = summaryId; this.metricCode = metricCode; this.value = value;
        this.applicability = applicability; this.qualityReason = qualityReason;
    }

    public UUID getSummaryId() { return summaryId; }
    public String getMetricCode() { return metricCode; }
    public BigDecimal getValue() { return value; }
    public String getApplicability() { return applicability; }
    public String getQualityReason() { return qualityReason; }

    public static final class Key implements Serializable {
        private UUID summaryId;
        private String metricCode;
        public Key() { }
        public Key(UUID summaryId, String metricCode) { this.summaryId = summaryId; this.metricCode = metricCode; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(summaryId, key.summaryId)
                    && Objects.equals(metricCode, key.metricCode);
        }
        @Override public int hashCode() { return Objects.hash(summaryId, metricCode); }
    }
}
