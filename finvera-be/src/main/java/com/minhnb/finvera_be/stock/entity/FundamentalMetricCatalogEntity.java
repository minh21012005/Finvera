package com.minhnb.finvera_be.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "fundamental_metric_catalog")
@IdClass(FundamentalMetricCatalogEntity.Key.class)
public class FundamentalMetricCatalogEntity {
    @Id @Column(name = "metric_code") private String metricCode;
    @Id @Column(name = "catalog_version") private String catalogVersion;
    private String category;
    @Column(name = "unit_type") private String unitType;
    private short scale;
    @Column(name = "sign_policy") private String signPolicy;
    @Column(name = "display_name_vi") private String displayNameVi;
    @Column(name = "display_name_en") private String displayNameEn;

    protected FundamentalMetricCatalogEntity() { }

    public FundamentalMetricCatalogEntity(String metricCode, String catalogVersion, String category,
            String unitType, short scale, String signPolicy, String displayNameVi, String displayNameEn) {
        this.metricCode = metricCode; this.catalogVersion = catalogVersion; this.category = category;
        this.unitType = unitType; this.scale = scale; this.signPolicy = signPolicy;
        this.displayNameVi = displayNameVi; this.displayNameEn = displayNameEn;
    }

    public String getMetricCode() { return metricCode; }
    public String getCatalogVersion() { return catalogVersion; }
    public String getCategory() { return category; }
    public String getUnitType() { return unitType; }
    public short getScale() { return scale; }
    public String getSignPolicy() { return signPolicy; }
    public String getDisplayNameVi() { return displayNameVi; }
    public String getDisplayNameEn() { return displayNameEn; }

    public static final class Key implements Serializable {
        private String metricCode;
        private String catalogVersion;
        public Key() { }
        public Key(String metricCode, String catalogVersion) {
            this.metricCode = metricCode; this.catalogVersion = catalogVersion;
        }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(metricCode, key.metricCode)
                    && Objects.equals(catalogVersion, key.catalogVersion);
        }
        @Override public int hashCode() { return Objects.hash(metricCode, catalogVersion); }
    }
}
