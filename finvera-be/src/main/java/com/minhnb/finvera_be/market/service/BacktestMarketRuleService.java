package com.minhnb.finvera_be.market.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** Published market-module API: completed sessions and versioned Vietnamese standard lot. */
@Service
public class BacktestMarketRuleService {
    public static final String LOT_RULE_VERSION="market-lot-v1";
    public static final long STANDARD_LOT=100;
    private final MarketReferenceDataService reference;
    public BacktestMarketRuleService(MarketReferenceDataService reference){this.reference=reference;}
    public record Rules(long lotSize,String lotRuleVersion,String sourceReference,Instant acceptedAt){}
    public record EligibleRange(LocalDate start,LocalDate end,List<String> warnings,String unavailableReason){}
    public Rules rules(String venue,LocalDate date){
        if(!List.of("HOSE","HNX","UPCOM").contains(venue))throw new IllegalArgumentException("UNSUPPORTED_VENUE");
        String source="UPCOM".equals(venue)?"VNX_DECISION_23_QD_HDTV_2026":"VNX_DECISION_22_QD_HDTV_2026";
        return new Rules(STANDARD_LOT,LOT_RULE_VERSION,source,Instant.parse("2026-09-13T00:00:00Z"));
    }
    public boolean isKnownTradingSession(String venue,LocalDate date){
        return reference.countTradingSessionsBetween(venue,date.minusDays(1),date)==1;
    }
}
