package com.minhnb.finvera_be.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "strategy_signal")
public class StrategySignalEntity {
    @Id private UUID id;
    @Column(name = "instrument_id") private UUID instrumentId;
    @Column(name = "strategy_code") private String strategyCode;
    @Column(name = "rule_version") private String ruleVersion;
    @Column(name = "as_of_trading_date") private LocalDate asOfTradingDate;
    private String direction;
    @Column(name = "entry_low", precision = 20, scale = 6) private BigDecimal entryLow;
    @Column(name = "entry_high", precision = 20, scale = 6) private BigDecimal entryHigh;
    @Column(name = "stop_loss", precision = 20, scale = 6) private BigDecimal stopLoss;
    @Column(precision = 20, scale = 6) private BigDecimal target1;
    @Column(precision = 20, scale = 6) private BigDecimal target2;
    @Column(name = "risk_reward", precision = 8, scale = 4) private BigDecimal riskReward;
    @Column(name = "risk_score") private Short riskScore;
    @Column(name = "risk_level") private String riskLevel;
    @Column(name = "input_set_hash", columnDefinition = "char(64)")
    @JdbcTypeCode(Types.CHAR)
    private String inputSetHash;
    @Column(name = "calculated_at") private Instant calculatedAt;
    @Column(name = "is_current") private boolean current;
    @Column(name = "supersedes_id") private UUID supersedesId;

    protected StrategySignalEntity() { }

    public StrategySignalEntity(UUID id, UUID instrumentId, String strategyCode, String ruleVersion,
            LocalDate asOfTradingDate, String direction, BigDecimal entryLow, BigDecimal entryHigh,
            BigDecimal stopLoss, BigDecimal target1, BigDecimal target2, BigDecimal riskReward, Integer riskScore,
            String riskLevel, String inputSetHash, Instant calculatedAt, boolean current, UUID supersedesId) {
        this.id = id; this.instrumentId = instrumentId; this.strategyCode = strategyCode;
        this.ruleVersion = ruleVersion; this.asOfTradingDate = asOfTradingDate; this.direction = direction;
        this.entryLow = entryLow; this.entryHigh = entryHigh; this.stopLoss = stopLoss; this.target1 = target1;
        this.target2 = target2; this.riskReward = riskReward;
        this.riskScore = riskScore == null ? null : riskScore.shortValue();
        this.riskLevel = riskLevel; this.inputSetHash = inputSetHash; this.calculatedAt = calculatedAt;
        this.current = current; this.supersedesId = supersedesId;
    }

    public UUID getId() { return id; }
    public UUID getInstrumentId() { return instrumentId; }
    public String getStrategyCode() { return strategyCode; }
    public String getRuleVersion() { return ruleVersion; }
    public LocalDate getAsOfTradingDate() { return asOfTradingDate; }
    public String getDirection() { return direction; }
    public BigDecimal getEntryLow() { return entryLow; }
    public BigDecimal getEntryHigh() { return entryHigh; }
    public BigDecimal getStopLoss() { return stopLoss; }
    public BigDecimal getTarget1() { return target1; }
    public BigDecimal getTarget2() { return target2; }
    public BigDecimal getRiskReward() { return riskReward; }
    public Integer getRiskScore() { return riskScore == null ? null : riskScore.intValue(); }
    public String getRiskLevel() { return riskLevel; }
    public String getInputSetHash() { return inputSetHash; }
    public Instant getCalculatedAt() { return calculatedAt; }
    public boolean isCurrent() { return current; }
    public void markSuperseded() { this.current = false; }
    public UUID getSupersedesId() { return supersedesId; }
}
