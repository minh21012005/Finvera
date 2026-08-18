package com.minhnb.finvera_be.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "sector_reference")
public class SectorReferenceEntity {
    @Id private UUID id;
    private String scheme;
    @Column(name = "scheme_version") private String schemeVersion;
    @Column(name = "sector_code") private String sectorCode;
    @Column(name = "display_name_vi") private String displayNameVi;
    @Column(name = "display_name_en") private String displayNameEn;

    protected SectorReferenceEntity() { }

    public SectorReferenceEntity(UUID id, String scheme, String schemeVersion, String sectorCode,
            String displayNameVi, String displayNameEn) {
        this.id = id; this.scheme = scheme; this.schemeVersion = schemeVersion; this.sectorCode = sectorCode;
        this.displayNameVi = displayNameVi; this.displayNameEn = displayNameEn;
    }

    public UUID getId() { return id; }
    public String getScheme() { return scheme; }
    public String getSchemeVersion() { return schemeVersion; }
    public String getSectorCode() { return sectorCode; }
    public String getDisplayNameVi() { return displayNameVi; }
    public String getDisplayNameEn() { return displayNameEn; }
}
