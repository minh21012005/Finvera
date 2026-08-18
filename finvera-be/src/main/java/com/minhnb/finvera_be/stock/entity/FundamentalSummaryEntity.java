package com.minhnb.finvera_be.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "fundamental_summary")
public class FundamentalSummaryEntity {
    @Id private UUID id;
    @Column(name = "instrument_id") private UUID instrumentId;
    @Column(name = "as_of_trading_date") private LocalDate asOfTradingDate;
    @Column(name = "rule_version") private String ruleVersion;
    @Column(name = "basis_period_end") private LocalDate basisPeriodEnd;
    @Column(name = "basis_period_label") private String basisPeriodLabel;
    @Column(name = "data_status") private String dataStatus;
    @Column(name = "calculated_at") private Instant calculatedAt;
    @Column(name = "reason_codes") @JdbcTypeCode(Types.ARRAY) private String[] reasonCodes;
    @Column(name = "supersedes_id") private UUID supersedesId;

    protected FundamentalSummaryEntity() { }

    public FundamentalSummaryEntity(UUID id, UUID instrumentId, LocalDate asOfTradingDate, String ruleVersion,
            LocalDate basisPeriodEnd, String basisPeriodLabel, String dataStatus, Instant calculatedAt,
            List<String> reasonCodes, UUID supersedesId) {
        this.id = id; this.instrumentId = instrumentId; this.asOfTradingDate = asOfTradingDate;
        this.ruleVersion = ruleVersion; this.basisPeriodEnd = basisPeriodEnd;
        this.basisPeriodLabel = basisPeriodLabel; this.dataStatus = dataStatus; this.calculatedAt = calculatedAt;
        this.reasonCodes = reasonCodes.toArray(String[]::new); this.supersedesId = supersedesId;
    }

    public UUID getId() { return id; }
    public UUID getInstrumentId() { return instrumentId; }
    public LocalDate getAsOfTradingDate() { return asOfTradingDate; }
    public String getRuleVersion() { return ruleVersion; }
    public LocalDate getBasisPeriodEnd() { return basisPeriodEnd; }
    public String getBasisPeriodLabel() { return basisPeriodLabel; }
    public String getDataStatus() { return dataStatus; }
    public Instant getCalculatedAt() { return calculatedAt; }
    public List<String> getReasonCodes() { return List.of(reasonCodes); }
    public UUID getSupersedesId() { return supersedesId; }
}
