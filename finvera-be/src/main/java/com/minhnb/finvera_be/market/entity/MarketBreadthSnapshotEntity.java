package com.minhnb.finvera_be.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.sql.Types;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "breadth_snapshot")
public class MarketBreadthSnapshotEntity {
    @Id private UUID id;
    @Column(name = "trading_date") private LocalDate tradingDate;
    @Column(name = "as_of") private Instant asOf;
    @Column(name = "calculated_at") private Instant calculatedAt;
    @Column(name = "universe_policy_version") private String universePolicyVersion;
    @Column(name = "universe_revision_hash", columnDefinition = "char(64)")
    @JdbcTypeCode(Types.CHAR)
    private String universeRevisionHash;
    private int advancing;
    private int declining;
    private int unchanged;
    private int eligible;
    private int unclassified;
    @Column(name = "data_status") private String dataStatus;
    @Column(name = "reason_codes")
    @JdbcTypeCode(Types.ARRAY)
    private String[] reasonCodes;
    @Column(name = "calculation_version") private String calculationVersion;
    @Column(name = "supersedes_id") private UUID supersedesId;

    protected MarketBreadthSnapshotEntity() { }

    public MarketBreadthSnapshotEntity(UUID id, LocalDate tradingDate, Instant asOf, Instant calculatedAt,
            String universePolicyVersion, String universeRevisionHash, int advancing, int declining,
            int unchanged, int eligible, int unclassified, String dataStatus, String calculationVersion,
            List<String> reasonCodes, UUID supersedesId) {
        this.id = id; this.tradingDate = tradingDate; this.asOf = asOf; this.calculatedAt = calculatedAt;
        this.universePolicyVersion = universePolicyVersion; this.universeRevisionHash = universeRevisionHash;
        this.advancing = advancing; this.declining = declining; this.unchanged = unchanged;
        this.eligible = eligible; this.unclassified = unclassified; this.dataStatus = dataStatus;
        this.calculationVersion = calculationVersion; this.reasonCodes = reasonCodes.toArray(String[]::new); this.supersedesId = supersedesId;
    }
    public UUID getId() { return id; }
    public LocalDate getTradingDate() { return tradingDate; }
    public Instant getAsOf() { return asOf; }
    public String getUniversePolicyVersion() { return universePolicyVersion; }
    public String getUniverseRevisionHash() { return universeRevisionHash; }
    public int getAdvancing() { return advancing; }
    public int getDeclining() { return declining; }
    public int getUnchanged() { return unchanged; }
    public int getEligible() { return eligible; }
    public int getUnclassified() { return unclassified; }
    public String getDataStatus() { return dataStatus; }
    public List<String> getReasonCodes() { return List.of(reasonCodes); }
}
