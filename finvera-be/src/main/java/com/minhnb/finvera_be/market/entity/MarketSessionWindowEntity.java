package com.minhnb.finvera_be.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "market_session_window")
public class MarketSessionWindowEntity {
    @Id private UUID id;
    private String venue;
    private String state;
    @Column(name = "start_local") private LocalTime startLocal;
    @Column(name = "end_local") private LocalTime endLocal;
    @Column(name = "effective_from") private LocalDate effectiveFrom;
    @Column(name = "effective_to") private LocalDate effectiveTo;
    @Column(name = "policy_version") private String policyVersion;
    @Column(name = "source_reference") private String sourceReference;

    protected MarketSessionWindowEntity() { }

    public MarketSessionWindowEntity(UUID id, String venue, String state, LocalTime startLocal, LocalTime endLocal,
            LocalDate effectiveFrom, LocalDate effectiveTo, String policyVersion, String sourceReference) {
        this.id = id; this.venue = venue; this.state = state; this.startLocal = startLocal;
        this.endLocal = endLocal; this.effectiveFrom = effectiveFrom; this.effectiveTo = effectiveTo;
        this.policyVersion = policyVersion; this.sourceReference = sourceReference;
    }

    public UUID getId() { return id; }
    public String getVenue() { return venue; }
    public String getState() { return state; }
    public LocalTime getStartLocal() { return startLocal; }
    public LocalTime getEndLocal() { return endLocal; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public String getPolicyVersion() { return policyVersion; }
}
