package com.minhnb.finvera_be.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "valuation_assessment")
public class ValuationAssessmentEntity {
    @Id private UUID id;
    @Column(name = "instrument_id") private UUID instrumentId;
    @Column(name = "as_of_trading_date") private LocalDate asOfTradingDate;
    @Column(name = "as_of") private Instant asOf;
    @Column(name = "rule_version") private String ruleVersion;
    private String classification;
    @Column(precision = 6, scale = 3) private BigDecimal score;
    @Column(name = "displayed_score") private Short displayedScore;
    private Short confidence;
    @Column(name = "used_own_history") private boolean usedOwnHistory;
    @Column(name = "used_sector") private boolean usedSector;
    @Column(name = "sector_reference_id") private UUID sectorReferenceId;
    @Column(name = "sector_constituent_count") private Integer sectorConstituentCount;
    @Column(name = "history_point_count") private Integer historyPointCount;
    @Column(name = "data_status") private String dataStatus;
    @Column(name = "reason_codes") @JdbcTypeCode(Types.ARRAY) private String[] reasonCodes;
    @Column(name = "calculated_at") private Instant calculatedAt;
    @Column(name = "is_current") private boolean current;
    @Column(name = "supersedes_id") private UUID supersedesId;

    protected ValuationAssessmentEntity() { }

    public ValuationAssessmentEntity(UUID id, UUID instrumentId, LocalDate asOfTradingDate, Instant asOf,
            String ruleVersion, String classification, BigDecimal score, Short displayedScore, Short confidence,
            boolean usedOwnHistory, boolean usedSector, UUID sectorReferenceId, Integer sectorConstituentCount,
            Integer historyPointCount, String dataStatus, List<String> reasonCodes, Instant calculatedAt,
            boolean current, UUID supersedesId) {
        this.id = id; this.instrumentId = instrumentId; this.asOfTradingDate = asOfTradingDate; this.asOf = asOf;
        this.ruleVersion = ruleVersion; this.classification = classification; this.score = score;
        this.displayedScore = displayedScore; this.confidence = confidence; this.usedOwnHistory = usedOwnHistory;
        this.usedSector = usedSector; this.sectorReferenceId = sectorReferenceId;
        this.sectorConstituentCount = sectorConstituentCount; this.historyPointCount = historyPointCount;
        this.dataStatus = dataStatus; this.reasonCodes = reasonCodes.toArray(String[]::new);
        this.calculatedAt = calculatedAt; this.current = current; this.supersedesId = supersedesId;
    }

    public UUID getId() { return id; }
    public UUID getInstrumentId() { return instrumentId; }
    public LocalDate getAsOfTradingDate() { return asOfTradingDate; }
    public Instant getAsOf() { return asOf; }
    public String getRuleVersion() { return ruleVersion; }
    public String getClassification() { return classification; }
    public BigDecimal getScore() { return score; }
    public Short getDisplayedScore() { return displayedScore; }
    public Short getConfidence() { return confidence; }
    public boolean isUsedOwnHistory() { return usedOwnHistory; }
    public boolean isUsedSector() { return usedSector; }
    public UUID getSectorReferenceId() { return sectorReferenceId; }
    public Integer getSectorConstituentCount() { return sectorConstituentCount; }
    public Integer getHistoryPointCount() { return historyPointCount; }
    public String getDataStatus() { return dataStatus; }
    public List<String> getReasonCodes() { return List.of(reasonCodes); }
    public Instant getCalculatedAt() { return calculatedAt; }
    public boolean isCurrent() { return current; }
    public void markSuperseded() { this.current = false; }
    public UUID getSupersedesId() { return supersedesId; }
}
