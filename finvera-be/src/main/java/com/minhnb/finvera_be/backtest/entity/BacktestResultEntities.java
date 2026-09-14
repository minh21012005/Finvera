package com.minhnb.finvera_be.backtest.entity;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Availability;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.EntryOutcome;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.EvidenceUnit;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.MetricCode;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.MetricUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class BacktestResultEntities {
    private BacktestResultEntities() { }

    @Entity @Table(name="backtest_metric")
    public static class Metric {
        @Id private UUID id; @Column(name="run_id",nullable=false) private UUID runId;
        @Enumerated(EnumType.STRING) @Column(name="metric_code",nullable=false,length=32) private MetricCode code;
        @Column(name="metric_value",precision=34,scale=8) private BigDecimal value;
        @Enumerated(EnumType.STRING) @Column(nullable=false,length=8) private MetricUnit unit;
        @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Availability availability;
        @Column(name="reason_code",length=64) private String reasonCode;
        @Column(name="metric_rule_version",nullable=false,length=40) private String ruleVersion;
        protected Metric() { }
        public Metric(UUID id,UUID runId,MetricCode code,BigDecimal value,MetricUnit unit,Availability availability,String reasonCode){
            this.id=id;this.runId=runId;this.code=code;this.value=value;this.unit=unit;
            this.availability=availability;this.reasonCode=reasonCode;this.ruleVersion="backtest-metrics-v1";
        }
        public UUID getId(){return id;} public UUID getRunId(){return runId;} public MetricCode getCode(){return code;}
        public BigDecimal getValue(){return value;} public Availability getAvailability(){return availability;}
        public MetricUnit getUnit(){return unit;} public String getReasonCode(){return reasonCode;} public String getRuleVersion(){return ruleVersion;}
    }

    @Entity @Table(name="backtest_entry_event")
    public static class EntryEvent {
        @Id private UUID id; @Column(name="run_id",nullable=false) private UUID runId;
        @Column(name="sequence_no",nullable=false) private int sequenceNo;
        @Column(name="signal_date",nullable=false) private LocalDate signalDate;
        @Column(name="execution_date") private LocalDate executionDate;
        @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private EntryOutcome outcome;
        @Column(name="reason_code",nullable=false,length=64) private String reasonCode;
        @Column(name="open_tranche_count",nullable=false) private short openTrancheCount;
        @Column(name="available_cash_vnd",precision=34,scale=12) private BigDecimal availableCashVnd;
        @Column(name="remaining_risk_vnd",precision=34,scale=12) private BigDecimal remainingRiskVnd;
        protected EntryEvent() { }
        public EntryEvent(UUID id,UUID runId,int sequenceNo,LocalDate signalDate,LocalDate executionDate,
                EntryOutcome outcome,String reasonCode,short openTrancheCount,BigDecimal availableCashVnd,BigDecimal remainingRiskVnd){
            this.id=id;this.runId=runId;this.sequenceNo=sequenceNo;this.signalDate=signalDate;
            this.executionDate=executionDate;this.outcome=outcome;this.reasonCode=reasonCode;
            this.openTrancheCount=openTrancheCount;this.availableCashVnd=availableCashVnd;this.remainingRiskVnd=remainingRiskVnd;
        }
        public UUID getId(){return id;} public UUID getRunId(){return runId;} public int getSequenceNo(){return sequenceNo;}
        public LocalDate getSignalDate(){return signalDate;} public LocalDate getExecutionDate(){return executionDate;}
        public EntryOutcome getOutcome(){return outcome;} public String getReasonCode(){return reasonCode;}
        public short getOpenTrancheCount(){return openTrancheCount;}
    }

    @Entity @Table(name="backtest_evidence")
    public static class Evidence {
        @Id private UUID id; @Column(name="run_id",nullable=false) private UUID runId;
        @Column(name="evidence_key",nullable=false,length=64) private String key;
        @Column(name="evidence_value",nullable=false,columnDefinition="text") private String value;
        @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private EvidenceUnit unit;
        protected Evidence() { }
        public Evidence(UUID id,UUID runId,String key,String value,EvidenceUnit unit){this.id=id;this.runId=runId;this.key=key;this.value=value;this.unit=unit;}
        public UUID getId(){return id;} public UUID getRunId(){return runId;} public String getKey(){return key;}
        public String getValue(){return value;} public EvidenceUnit getUnit(){return unit;}
    }
}
