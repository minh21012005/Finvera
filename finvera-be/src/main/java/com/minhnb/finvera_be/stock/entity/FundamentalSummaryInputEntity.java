package com.minhnb.finvera_be.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "fundamental_summary_input")
@IdClass(FundamentalSummaryInputEntity.Key.class)
public class FundamentalSummaryInputEntity {
    @Id @Column(name = "summary_id") private UUID summaryId;
    @Id @Column(name = "input_role") private String inputRole;
    @Column(name = "report_id") private UUID reportId;

    protected FundamentalSummaryInputEntity() { }

    public FundamentalSummaryInputEntity(UUID summaryId, String inputRole, UUID reportId) {
        this.summaryId = summaryId; this.inputRole = inputRole; this.reportId = reportId;
    }

    public UUID getSummaryId() { return summaryId; }
    public String getInputRole() { return inputRole; }
    public UUID getReportId() { return reportId; }

    public static final class Key implements Serializable {
        private UUID summaryId;
        private String inputRole;
        public Key() { }
        public Key(UUID summaryId, String inputRole) { this.summaryId = summaryId; this.inputRole = inputRole; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(summaryId, key.summaryId)
                    && Objects.equals(inputRole, key.inputRole);
        }
        @Override public int hashCode() { return Objects.hash(summaryId, inputRole); }
    }
}
