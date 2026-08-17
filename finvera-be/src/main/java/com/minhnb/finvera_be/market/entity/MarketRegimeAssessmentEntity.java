package com.minhnb.finvera_be.market.entity;

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
@Table(name = "regime_assessment")
public class MarketRegimeAssessmentEntity {
    @Id private UUID id;
    @Column(name = "trading_date") private LocalDate tradingDate;
    @Column(name = "as_of") private Instant asOf;
    @Column(name = "calculated_at") private Instant calculatedAt;
    @Column(name = "rule_version") private String ruleVersion;
    private String label;
    private Short score;
    private Short confidence;
    @Column(name = "data_status") private String dataStatus;
    @Column(precision = 5, scale = 2) private BigDecimal completeness;
    @Column(name = "factor_agreement", precision = 5, scale = 2) private BigDecimal factorAgreement;
    @Column(name = "boundary_distance", precision = 5, scale = 2) private BigDecimal boundaryDistance;
    private boolean renormalized;
    @Column(name = "reason_codes") @JdbcTypeCode(Types.ARRAY) private String[] reasonCodes;
    @Column(name = "supersedes_id") private UUID supersedesId;

    protected MarketRegimeAssessmentEntity() { }

    public MarketRegimeAssessmentEntity(UUID id, LocalDate tradingDate, Instant asOf, Instant calculatedAt,
            String ruleVersion, String label, Integer score, Integer confidence, String dataStatus,
            BigDecimal completeness, BigDecimal factorAgreement, BigDecimal boundaryDistance,
            boolean renormalized, List<String> reasonCodes, UUID supersedesId) {
        this.id = id; this.tradingDate = tradingDate; this.asOf = asOf; this.calculatedAt = calculatedAt;
        this.ruleVersion = ruleVersion; this.label = label;
        this.score = score == null ? null : score.shortValue();
        this.confidence = confidence == null ? null : confidence.shortValue();
        this.dataStatus = dataStatus; this.completeness = completeness; this.factorAgreement = factorAgreement;
        this.boundaryDistance = boundaryDistance; this.renormalized = renormalized;
        this.reasonCodes = reasonCodes.toArray(String[]::new); this.supersedesId = supersedesId;
    }

    public UUID getId() { return id; }
    public LocalDate getTradingDate() { return tradingDate; }
    public Instant getAsOf() { return asOf; }
    public Instant getCalculatedAt() { return calculatedAt; }
    public String getRuleVersion() { return ruleVersion; }
    public String getLabel() { return label; }
    public Integer getScore() { return score == null ? null : score.intValue(); }
    public Integer getConfidence() { return confidence == null ? null : confidence.intValue(); }
    public String getDataStatus() { return dataStatus; }
    public BigDecimal getCompleteness() { return completeness; }
    public BigDecimal getFactorAgreement() { return factorAgreement; }
    public BigDecimal getBoundaryDistance() { return boundaryDistance; }
    public boolean isRenormalized() { return renormalized; }
    public List<String> getReasonCodes() { return List.of(reasonCodes); }
    public UUID getSupersedesId() { return supersedesId; }
}
