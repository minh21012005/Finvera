package com.minhnb.finvera_be.backtest.entity;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Assumptions;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.RunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "backtest_run")
public class BacktestRunEntity {
    @Id private UUID id;
    @Column(name="owner_id",nullable=false) private UUID ownerId;
    @Column(name="idempotency_key",length=100) private String idempotencyKey;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private RunStatus status;
    @Version @Column(name="row_version",nullable=false) private long rowVersion;
    @Column(name="strategy_code",nullable=false,length=32) private String strategyCode;
    @Column(name="strategy_rule_version",nullable=false,length=40) private String strategyRuleVersion;
    @Column(nullable=false,length=10) private String symbol;
    @Column(name="instrument_id",nullable=false) private UUID instrumentId;
    @Column(nullable=false,length=16) private String venue;
    @Column(name="reporting_start",nullable=false) private LocalDate reportingStart;
    @Column(name="reporting_end",nullable=false) private LocalDate reportingEnd;
    @Column(name="initial_capital_vnd",nullable=false,precision=20,scale=6) private BigDecimal initialCapitalVnd;
    @Column(name="risk_per_tranche_rate",nullable=false,precision=20,scale=8) private BigDecimal riskPerTrancheRate;
    @Column(name="max_aggregate_open_risk_rate",nullable=false,precision=20,scale=8) private BigDecimal maxAggregateOpenRiskRate;
    @Column(name="costs_excluded",nullable=false) private boolean costsExcluded;
    @Column(name="entry_fee_rate",nullable=false,precision=20,scale=8) private BigDecimal entryFeeRate;
    @Column(name="exit_fee_rate",nullable=false,precision=20,scale=8) private BigDecimal exitFeeRate;
    @Column(name="sell_tax_rate",nullable=false,precision=20,scale=8) private BigDecimal sellTaxRate;
    @Column(name="entry_slippage_rate",nullable=false,precision=20,scale=8) private BigDecimal entrySlippageRate;
    @Column(name="exit_slippage_rate",nullable=false,precision=20,scale=8) private BigDecimal exitSlippageRate;
    @Column(name="engine_rule_version",nullable=false,length=40) private String engineRuleVersion;
    @Column(name="metrics_rule_version",nullable=false,length=40) private String metricsRuleVersion;
    @Column(name="pyramiding_rule_version",nullable=false,length=40) private String pyramidingRuleVersion;
    @Column(name="sizing_rule_version",nullable=false,length=40) private String sizingRuleVersion;
    @Column(name="lot_rule_version",nullable=false,length=40) private String lotRuleVersion;
    @Column(name="data_cutoff_accepted_at",nullable=false) private Instant dataCutoffAcceptedAt;
    @Column(name="input_fingerprint",length=64) private String inputFingerprint;
    @Column(name="processed_sessions",nullable=false) private int processedSessions;
    @Column(name="total_sessions") private Integer totalSessions;
    @Column(name="attempt_count",nullable=false) private short attemptCount;
    @Column(name="heartbeat_at") private Instant heartbeatAt;
    @Column(name="reason_code",length=64) private String reasonCode;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="started_at") private Instant startedAt;
    @Column(name="completed_at") private Instant completedAt;

    protected BacktestRunEntity() { }

    public BacktestRunEntity(UUID id, UUID ownerId, String idempotencyKey,
            String strategyCode, String symbol, UUID instrumentId, String venue,
            LocalDate reportingStart, LocalDate reportingEnd, Assumptions assumptions,
            Instant cutoff, Instant createdAt) {
        this.id=Objects.requireNonNull(id); this.ownerId=Objects.requireNonNull(ownerId);
        this.idempotencyKey=idempotencyKey; this.status=RunStatus.QUEUED;
        this.strategyCode=Objects.requireNonNull(strategyCode); this.strategyRuleVersion="strategy-signal-v1";
        this.symbol=Objects.requireNonNull(symbol); this.instrumentId=Objects.requireNonNull(instrumentId);
        this.venue=Objects.requireNonNull(venue); this.reportingStart=Objects.requireNonNull(reportingStart);
        this.reportingEnd=Objects.requireNonNull(reportingEnd);
        this.initialCapitalVnd=assumptions.initialCapitalVnd();
        this.riskPerTrancheRate=assumptions.riskPerTrancheRate();
        this.maxAggregateOpenRiskRate=assumptions.maxAggregateOpenRiskRate();
        this.costsExcluded=assumptions.costs().excluded();
        this.entryFeeRate=assumptions.costs().entryFeeRate(); this.exitFeeRate=assumptions.costs().exitFeeRate();
        this.sellTaxRate=assumptions.costs().sellTaxRate(); this.entrySlippageRate=assumptions.costs().entrySlippageRate();
        this.exitSlippageRate=assumptions.costs().exitSlippageRate();
        this.engineRuleVersion="backtest-engine-v1"; this.metricsRuleVersion="backtest-metrics-v1";
        this.pyramidingRuleVersion="pyramiding-v1"; this.sizingRuleVersion="position-sizing-v1";
        this.lotRuleVersion="market-lot-v1"; this.dataCutoffAcceptedAt=Objects.requireNonNull(cutoff);
        this.createdAt=Objects.requireNonNull(createdAt);
    }

    public void claim(Instant now) {
        requireStatus(RunStatus.QUEUED); if (attemptCount >= 2) throw new IllegalStateException("ATTEMPTS_EXHAUSTED");
        status=RunStatus.RUNNING; attemptCount++; startedAt=startedAt==null?now:startedAt; heartbeatAt=now;
    }
    public void progress(int processed, int total, Instant now) {
        requireStatus(RunStatus.RUNNING);
        if(processed<0||total<0||processed>total) throw new IllegalArgumentException("INVALID_PROGRESS");
        processedSessions=processed; totalSessions=total; heartbeatAt=now;
    }
    public void requeue() { requireStatus(RunStatus.RUNNING); status=RunStatus.QUEUED; heartbeatAt=null; }
    public void complete(String fingerprint, int sessions, Instant now) {
        requireStatus(RunStatus.RUNNING); if(fingerprint==null||fingerprint.length()!=64) throw new IllegalArgumentException("INVALID_FINGERPRINT");
        inputFingerprint=fingerprint; processedSessions=sessions; totalSessions=sessions;
        status=RunStatus.COMPLETED; heartbeatAt=null; completedAt=now;
    }
    public void withhold(String reason, Instant now) { terminal(RunStatus.WITHHELD,reason,now); }
    public void fail(String reason, Instant now) { terminal(RunStatus.FAILED,reason,now); }
    private void terminal(RunStatus target,String reason,Instant now){requireStatus(RunStatus.RUNNING);status=target;reasonCode=Objects.requireNonNull(reason);heartbeatAt=null;completedAt=now;}
    private void requireStatus(RunStatus expected){if(status!=expected)throw new IllegalStateException("INVALID_RUN_TRANSITION");}

    public UUID getId(){return id;} public UUID getOwnerId(){return ownerId;}
    public String getIdempotencyKey(){return idempotencyKey;} public RunStatus getStatus(){return status;}
    public long getRowVersion(){return rowVersion;} public String getStrategyCode(){return strategyCode;}
    public String getSymbol(){return symbol;} public UUID getInstrumentId(){return instrumentId;}
    public String getVenue(){return venue;} public LocalDate getReportingStart(){return reportingStart;}
    public LocalDate getReportingEnd(){return reportingEnd;} public Instant getDataCutoffAcceptedAt(){return dataCutoffAcceptedAt;}
    public int getProcessedSessions(){return processedSessions;} public Integer getTotalSessions(){return totalSessions;}
    public short getAttemptCount(){return attemptCount;} public Instant getHeartbeatAt(){return heartbeatAt;}
    public String getReasonCode(){return reasonCode;} public Instant getCreatedAt(){return createdAt;}
    public Instant getStartedAt(){return startedAt;} public Instant getCompletedAt(){return completedAt;}
    public String getInputFingerprint(){return inputFingerprint;}
    public String getStrategyRuleVersion(){return strategyRuleVersion;} public BigDecimal getInitialCapitalVnd(){return initialCapitalVnd;}
    public BigDecimal getRiskPerTrancheRate(){return riskPerTrancheRate;} public BigDecimal getMaxAggregateOpenRiskRate(){return maxAggregateOpenRiskRate;}
    public boolean isCostsExcluded(){return costsExcluded;} public BigDecimal getEntryFeeRate(){return entryFeeRate;}
    public BigDecimal getExitFeeRate(){return exitFeeRate;} public BigDecimal getSellTaxRate(){return sellTaxRate;}
    public BigDecimal getEntrySlippageRate(){return entrySlippageRate;} public BigDecimal getExitSlippageRate(){return exitSlippageRate;}
    public String getEngineRuleVersion(){return engineRuleVersion;} public String getMetricsRuleVersion(){return metricsRuleVersion;}
    public String getPyramidingRuleVersion(){return pyramidingRuleVersion;} public String getSizingRuleVersion(){return sizingRuleVersion;}
    public String getLotRuleVersion(){return lotRuleVersion;}
}
