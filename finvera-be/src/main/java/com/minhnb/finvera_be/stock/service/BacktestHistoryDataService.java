package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.SessionBar;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.*;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.DailyBarPoint;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.IndicatorSnapshot;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1;
import com.minhnb.finvera_be.stock.entity.*;
import com.minhnb.finvera_be.stock.repository.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Published stock-module application API for point-in-time backtest snapshots. */
@Service
public class BacktestHistoryDataService {
    private static final int WARMUP_CALENDAR_DAYS=500;
    private final EquityDailyBarRepository bars;
    private final TechnicalIndicatorResultRepository results;
    private final TechnicalIndicatorValueRepository values;
    public BacktestHistoryDataService(EquityDailyBarRepository bars,TechnicalIndicatorResultRepository results,
            TechnicalIndicatorValueRepository values){this.bars=bars;this.results=results;this.values=values;}

    public record HistoricalSession(SessionBar bar,boolean reporting,StrategySignalV1.EntryEvaluation evaluation){}
    public record Snapshot(List<HistoricalSession> sessions,String fingerprint,String adjustmentBasis,
            List<String> sources,String unavailableReason){}

    @Transactional(readOnly=true)
    public Snapshot load(UUID instrumentId,StrategyCode strategy,LocalDate reportingStart,LocalDate reportingEnd,Instant cutoff){
        LocalDate warmup=reportingStart.minusDays(WARMUP_CALENDAR_DAYS);
        List<EquityDailyBarEntity> barRows=bars.findHistoricalSnapshot(instrumentId,warmup,reportingEnd,cutoff);
        if(barRows.stream().noneMatch(b->!b.getTradingDate().isBefore(reportingStart)))
            return unavailable("PRICE_HISTORY_UNAVAILABLE");
        Set<String> adjustmentBases=barRows.stream().map(EquityDailyBarEntity::getAdjustmentStatus).collect(Collectors.toSet());
        if(adjustmentBases.size()!=1)return unavailable("ADJUSTMENT_BASIS_UNAVAILABLE");
        String adjustmentBasis=adjustmentBases.iterator().next();
        if(!Set.of("RAW","PROVIDER_ADJUSTED").contains(adjustmentBasis))
            return unavailable("DUAL_PRICE_BASIS_UNSUPPORTED");
        List<String> sources=barRows.stream().map(EquityDailyBarEntity::getSource).filter(Objects::nonNull).distinct().sorted().toList();
        List<TechnicalIndicatorResultEntity> resultRows=results.findHistoricalSnapshot(instrumentId,
                com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.RULE_VERSION,warmup,reportingEnd,cutoff);
        Map<UUID,List<TechnicalIndicatorValueEntity>> byResult=values.findByResultIdIn(resultRows.stream().map(TechnicalIndicatorResultEntity::getId).toList())
                .stream().collect(Collectors.groupingBy(TechnicalIndicatorValueEntity::getResultId));
        Map<LocalDate,Map<IndicatorCode,IndicatorSnapshot>> indicators=new HashMap<>();
        List<String> fingerprintParts=new ArrayList<>();
        for(var r:resultRows){
            if(!r.getAsOfTradingDate().equals(r.getWindowEndDate()))return unavailable("INCOHERENT_INDICATOR_WINDOW");
            if(!adjustmentBasis.equals(r.getAdjustmentStatus()))return unavailable("INDICATOR_PRICE_BASIS_MISMATCH");
            IndicatorCode code;try{code=IndicatorCode.valueOf(r.getIndicatorCode());}catch(IllegalArgumentException ex){continue;}
            Map<IndicatorComponent,BigDecimal> components=new EnumMap<>(IndicatorComponent.class);
            for(var v:byResult.getOrDefault(r.getId(),List.of()))try{components.put(IndicatorComponent.valueOf(v.getComponentCode()),v.getValue());}catch(IllegalArgumentException ignored){}
            MetricApplicability app=("CURRENT".equals(r.getDataStatus())||r.getQualityReason()==null)?MetricApplicability.DEFINED:MetricApplicability.MISSING;
            indicators.computeIfAbsent(r.getAsOfTradingDate(),x->new EnumMap<>(IndicatorCode.class))
                    .put(code,new IndicatorSnapshot(app,Map.copyOf(components),r.getQualityReason()));
            fingerprintParts.add("I:"+r.getId());
        }
        List<HistoricalSession> sessions=new ArrayList<>(); List<DailyBarPoint> recent=new ArrayList<>();
        LocalDate priorDate=null;
        for(var b:barRows){
            if(b.getHighPrice()==null||b.getLowPrice()==null||b.getClosePrice()==null
                    ||b.getAdjustmentStatus()==null||b.getSource()==null||b.getObservedAt()==null||b.getAcceptedAt()==null)
                return unavailable("INCOHERENT_PRICE_HISTORY");
            recent.add(new DailyBarPoint(b.getTradingDate(),b.getClosePrice(),b.getHighPrice(),b.getLowPrice()));
            if(recent.size()>21)recent.remove(0);
            var input=new StrategySignalV1.StrategyInputs(b.getClosePrice(),indicators.getOrDefault(b.getTradingDate(),Map.of()),
                    priorDate==null?Map.of():indicators.getOrDefault(priorDate,Map.of()),List.copyOf(recent));
            var eval=StrategySignalV1.evaluate(strategy,input);
            SessionBar bar=new SessionBar(b.getId(),b.getTradingDate(),b.getOpenPrice(),b.getHighPrice(),b.getLowPrice(),b.getClosePrice(),
                    b.getAdjustmentFactor()==null?BigDecimal.ONE:b.getAdjustmentFactor(),b.getAdjustmentStatus(),b.getSource(),b.getObservedAt(),b.getAcceptedAt());
            sessions.add(new HistoricalSession(bar,!b.getTradingDate().isBefore(reportingStart),eval));
            fingerprintParts.add("B:"+b.getId());priorDate=b.getTradingDate();
        }
        return new Snapshot(List.copyOf(sessions),sha256(String.join("|",fingerprintParts)),adjustmentBasis,sources,null);
    }
    private static Snapshot unavailable(String reason){return new Snapshot(List.of(),null,null,List.of(),reason);}
    private static String sha256(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
