package com.minhnb.finvera_be.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "market_calendar_day")
public class MarketCalendarDayEntity {
    @Id private UUID id;
    private String venue;
    @Column(name = "trading_date") private LocalDate tradingDate;
    @Column(name = "is_trading_day") private boolean tradingDay;
    @Column(name = "policy_version") private String policyVersion;
    @Column(name = "source_reference") private String sourceReference;
    @Column(name = "reason_code") private String reasonCode;
    @Column(name = "accepted_at") private Instant acceptedAt;

    protected MarketCalendarDayEntity() { }

    public MarketCalendarDayEntity(UUID id, String venue, LocalDate tradingDate, boolean tradingDay,
            String policyVersion, String sourceReference, String reasonCode, Instant acceptedAt) {
        this.id = id; this.venue = venue; this.tradingDate = tradingDate; this.tradingDay = tradingDay;
        this.policyVersion = policyVersion; this.sourceReference = sourceReference; this.reasonCode = reasonCode;
        this.acceptedAt = acceptedAt;
    }

    public UUID getId() { return id; }
    public String getVenue() { return venue; }
    public LocalDate getTradingDate() { return tradingDate; }
    public boolean isTradingDay() { return tradingDay; }
    public String getPolicyVersion() { return policyVersion; }
    public String getReasonCode() { return reasonCode; }
}
