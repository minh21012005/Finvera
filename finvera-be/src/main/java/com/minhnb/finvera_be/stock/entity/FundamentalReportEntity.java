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
@Table(name = "fundamental_report")
public class FundamentalReportEntity {
    @Id private UUID id;
    @Column(name = "instrument_id") private UUID instrumentId;
    @Column(name = "ingestion_record_id") private UUID ingestionRecordId;
    @Column(name = "period_type") private String periodType;
    @Column(name = "fiscal_year") private short fiscalYear;
    @Column(name = "fiscal_quarter") private Short fiscalQuarter;
    @Column(name = "period_start") private LocalDate periodStart;
    @Column(name = "period_end") private LocalDate periodEnd;
    @Column(name = "report_kind") private String reportKind;
    @Column(name = "audit_status") private String auditStatus;
    @Column(name = "currency", columnDefinition = "char(3)")
    @JdbcTypeCode(Types.CHAR)
    private String currency;
    @Column(name = "unit_scale") private int unitScale;
    @Column(name = "catalog_version") private String catalogVersion;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "observed_at") private Instant observedAt;
    @Column(name = "accepted_at") private Instant acceptedAt;
    private String source;
    private int revision;
    @Column(name = "is_current") private boolean current;
    @Column(name = "supersedes_id") private UUID supersedesId;
    @Column(name = "restatement_reason") private String restatementReason;

    protected FundamentalReportEntity() { }

    public FundamentalReportEntity(UUID id, UUID instrumentId, UUID ingestionRecordId, String periodType,
            short fiscalYear, Short fiscalQuarter, LocalDate periodStart, LocalDate periodEnd, String reportKind,
            String auditStatus, String currency, int unitScale, String catalogVersion, Instant publishedAt,
            Instant observedAt, Instant acceptedAt, String source, int revision, boolean current,
            UUID supersedesId, String restatementReason) {
        this.id = id; this.instrumentId = instrumentId; this.ingestionRecordId = ingestionRecordId;
        this.periodType = periodType; this.fiscalYear = fiscalYear; this.fiscalQuarter = fiscalQuarter;
        this.periodStart = periodStart; this.periodEnd = periodEnd; this.reportKind = reportKind;
        this.auditStatus = auditStatus; this.currency = currency; this.unitScale = unitScale;
        this.catalogVersion = catalogVersion; this.publishedAt = publishedAt; this.observedAt = observedAt;
        this.acceptedAt = acceptedAt; this.source = source; this.revision = revision; this.current = current;
        this.supersedesId = supersedesId; this.restatementReason = restatementReason;
    }

    public UUID getId() { return id; }
    public UUID getInstrumentId() { return instrumentId; }
    public UUID getIngestionRecordId() { return ingestionRecordId; }
    public String getPeriodType() { return periodType; }
    public short getFiscalYear() { return fiscalYear; }
    public Short getFiscalQuarter() { return fiscalQuarter; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public String getReportKind() { return reportKind; }
    public String getAuditStatus() { return auditStatus; }
    public String getCurrency() { return currency; }
    public int getUnitScale() { return unitScale; }
    public String getCatalogVersion() { return catalogVersion; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public String getSource() { return source; }
    public int getRevision() { return revision; }
    public boolean isCurrent() { return current; }
    public void markSuperseded() { this.current = false; }
    public UUID getSupersedesId() { return supersedesId; }
    public String getRestatementReason() { return restatementReason; }
}
