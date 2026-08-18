package com.minhnb.finvera_be.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "equity_profile")
public class EquityProfileEntity {
    @Id private UUID id;
    @Column(name = "instrument_id") private UUID instrumentId;
    @Column(name = "company_name_vi") private String companyNameVi;
    @Column(name = "company_name_en") private String companyNameEn;
    @Column(name = "sector_reference_id") private UUID sectorReferenceId;
    @Column(name = "shares_outstanding") private Long sharesOutstanding;
    @Column(name = "free_float_ratio", precision = 9, scale = 6) private BigDecimal freeFloatRatio;
    @Column(name = "listing_status") private String listingStatus;
    @Column(name = "effective_from") private LocalDate effectiveFrom;
    @Column(name = "effective_to") private LocalDate effectiveTo;
    private String source;
    @Column(name = "source_revision") private String sourceRevision;
    @Column(name = "quality_reason") private String qualityReason;

    protected EquityProfileEntity() { }

    public EquityProfileEntity(UUID id, UUID instrumentId, String companyNameVi, String companyNameEn,
            UUID sectorReferenceId, Long sharesOutstanding, BigDecimal freeFloatRatio, String listingStatus,
            LocalDate effectiveFrom, LocalDate effectiveTo, String source, String sourceRevision,
            String qualityReason) {
        this.id = id; this.instrumentId = instrumentId; this.companyNameVi = companyNameVi;
        this.companyNameEn = companyNameEn; this.sectorReferenceId = sectorReferenceId;
        this.sharesOutstanding = sharesOutstanding; this.freeFloatRatio = freeFloatRatio;
        this.listingStatus = listingStatus; this.effectiveFrom = effectiveFrom; this.effectiveTo = effectiveTo;
        this.source = source; this.sourceRevision = sourceRevision; this.qualityReason = qualityReason;
    }

    public UUID getId() { return id; }
    public UUID getInstrumentId() { return instrumentId; }
    public String getCompanyNameVi() { return companyNameVi; }
    public String getCompanyNameEn() { return companyNameEn; }
    public UUID getSectorReferenceId() { return sectorReferenceId; }
    public Long getSharesOutstanding() { return sharesOutstanding; }
    public BigDecimal getFreeFloatRatio() { return freeFloatRatio; }
    public String getListingStatus() { return listingStatus; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public String getSource() { return source; }
    public String getSourceRevision() { return sourceRevision; }
    public String getQualityReason() { return qualityReason; }
}
