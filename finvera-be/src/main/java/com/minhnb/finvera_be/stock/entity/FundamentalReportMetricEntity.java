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
@Table(name = "fundamental_report_metric")
@IdClass(FundamentalReportMetricEntity.Key.class)
public class FundamentalReportMetricEntity {
    @Id @Column(name = "report_id") private UUID reportId;
    @Id @Column(name = "metric_code") private String metricCode;
    @Column(precision = 28, scale = 6) private BigDecimal value;
    private String applicability;
    @Column(name = "quality_reason") private String qualityReason;

    protected FundamentalReportMetricEntity() { }

    public FundamentalReportMetricEntity(UUID reportId, String metricCode, BigDecimal value, String applicability,
            String qualityReason) {
        this.reportId = reportId; this.metricCode = metricCode; this.value = value;
        this.applicability = applicability; this.qualityReason = qualityReason;
    }

    public UUID getReportId() { return reportId; }
    public String getMetricCode() { return metricCode; }
    public BigDecimal getValue() { return value; }
    public String getApplicability() { return applicability; }
    public String getQualityReason() { return qualityReason; }

    public static final class Key implements Serializable {
        private UUID reportId;
        private String metricCode;
        public Key() { }
        public Key(UUID reportId, String metricCode) { this.reportId = reportId; this.metricCode = metricCode; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(reportId, key.reportId)
                    && Objects.equals(metricCode, key.metricCode);
        }
        @Override public int hashCode() { return Objects.hash(reportId, metricCode); }
    }
}
