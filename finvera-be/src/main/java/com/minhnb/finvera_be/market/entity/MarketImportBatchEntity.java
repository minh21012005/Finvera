package com.minhnb.finvera_be.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

/** Immutable provenance record for one accepted canonical historical-data package. */
@Entity
@Table(name = "market_import_batch")
public class MarketImportBatchEntity {
    @Id private UUID id;
    @Column(name = "contract_version") private String contractVersion;
    @Column(name = "tool_name") private String toolName;
    @Column(name = "tool_version") private String toolVersion;
    @Column(name = "upstream_source") private String upstreamSource;
    @Column(name = "package_sha256", columnDefinition = "char(64)")
    @JdbcTypeCode(Types.CHAR)
    private String packageSha256;
    @Column(name = "range_start") private LocalDate rangeStart;
    @Column(name = "range_end") private LocalDate rangeEnd;
    @Column(name = "generated_at") private Instant generatedAt;
    @Column(name = "received_at") private Instant receivedAt;
    private String status;
    @Column(name = "record_count") private long recordCount;
    @Column(name = "rejection_reason") private String rejectionReason;

    protected MarketImportBatchEntity() { }

    public MarketImportBatchEntity(UUID id, String contractVersion, String toolName, String toolVersion,
            String upstreamSource, String packageSha256, LocalDate rangeStart, LocalDate rangeEnd,
            Instant generatedAt, Instant receivedAt, String status, long recordCount, String rejectionReason) {
        this.id=id; this.contractVersion=contractVersion; this.toolName=toolName; this.toolVersion=toolVersion;
        this.upstreamSource=upstreamSource; this.packageSha256=packageSha256; this.rangeStart=rangeStart;
        this.rangeEnd=rangeEnd; this.generatedAt=generatedAt; this.receivedAt=receivedAt; this.status=status;
        this.recordCount=recordCount; this.rejectionReason=rejectionReason;
    }

    public UUID getId() { return id; }
    public String getPackageSha256() { return packageSha256; }
    public String getStatus() { return status; }
    public long getRecordCount() { return recordCount; }
}
