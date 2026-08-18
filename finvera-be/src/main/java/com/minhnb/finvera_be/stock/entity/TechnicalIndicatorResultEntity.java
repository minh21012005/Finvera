package com.minhnb.finvera_be.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "technical_indicator_result")
public class TechnicalIndicatorResultEntity {
    @Id private UUID id;
    @Column(name = "instrument_id") private UUID instrumentId;
    @Column(name = "indicator_code") private String indicatorCode;
    @Column(name = "rule_version") private String ruleVersion;
    @Column(name = "as_of_trading_date") private LocalDate asOfTradingDate;
    @Column(name = "window_start_date") private LocalDate windowStartDate;
    @Column(name = "window_end_date") private LocalDate windowEndDate;
    @Column(name = "input_bar_count") private int inputBarCount;
    @Column(name = "input_set_hash", columnDefinition = "char(64)")
    @JdbcTypeCode(Types.CHAR)
    private String inputSetHash;
    @Column(name = "adjustment_status") private String adjustmentStatus;
    @Column(name = "data_status") private String dataStatus;
    @Column(name = "quality_reason") private String qualityReason;
    @Column(name = "calculated_at") private Instant calculatedAt;
    @Column(name = "is_current") private boolean current;
    @Column(name = "supersedes_id") private UUID supersedesId;

    protected TechnicalIndicatorResultEntity() { }

    public TechnicalIndicatorResultEntity(UUID id, UUID instrumentId, String indicatorCode, String ruleVersion,
            LocalDate asOfTradingDate, LocalDate windowStartDate, LocalDate windowEndDate, int inputBarCount,
            String inputSetHash, String adjustmentStatus, String dataStatus, String qualityReason,
            Instant calculatedAt, boolean current, UUID supersedesId) {
        this.id = id; this.instrumentId = instrumentId; this.indicatorCode = indicatorCode;
        this.ruleVersion = ruleVersion; this.asOfTradingDate = asOfTradingDate;
        this.windowStartDate = windowStartDate; this.windowEndDate = windowEndDate;
        this.inputBarCount = inputBarCount; this.inputSetHash = inputSetHash;
        this.adjustmentStatus = adjustmentStatus; this.dataStatus = dataStatus; this.qualityReason = qualityReason;
        this.calculatedAt = calculatedAt; this.current = current; this.supersedesId = supersedesId;
    }

    public UUID getId() { return id; }
    public UUID getInstrumentId() { return instrumentId; }
    public String getIndicatorCode() { return indicatorCode; }
    public String getRuleVersion() { return ruleVersion; }
    public LocalDate getAsOfTradingDate() { return asOfTradingDate; }
    public LocalDate getWindowStartDate() { return windowStartDate; }
    public LocalDate getWindowEndDate() { return windowEndDate; }
    public int getInputBarCount() { return inputBarCount; }
    public String getInputSetHash() { return inputSetHash; }
    public String getAdjustmentStatus() { return adjustmentStatus; }
    public String getDataStatus() { return dataStatus; }
    public String getQualityReason() { return qualityReason; }
    public Instant getCalculatedAt() { return calculatedAt; }
    public boolean isCurrent() { return current; }
    public void markSuperseded() { this.current = false; }
    public UUID getSupersedesId() { return supersedesId; }
}
