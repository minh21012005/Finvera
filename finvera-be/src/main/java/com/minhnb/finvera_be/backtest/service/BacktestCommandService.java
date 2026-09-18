package com.minhnb.finvera_be.backtest.service;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.*;
import com.minhnb.finvera_be.backtest.dto.BacktestDtos.*;
import com.minhnb.finvera_be.backtest.entity.BacktestRunEntity;
import com.minhnb.finvera_be.backtest.repository.BacktestRunRepository;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.BacktestMarketRuleService;
import com.minhnb.finvera_be.portfolio.service.OwnerScopedAccess;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BacktestCommandService {
    public record RunQueued(UUID runId){}
    private final BacktestRunRepository runs; private final MarketReferenceDataService market;
    private final BacktestMarketRuleService marketRules; private final OwnerScopedAccess owners; private final ApplicationEventPublisher events; private final Clock clock;
    public BacktestCommandService(BacktestRunRepository runs,MarketReferenceDataService market,OwnerScopedAccess owners,
            ApplicationEventPublisher events,Clock clock,BacktestMarketRuleService marketRules){this.runs=runs;this.market=market;this.owners=owners;this.events=events;this.clock=clock;this.marketRules=marketRules;}
    @Transactional
    public RunSummary create(CreateRequest request,String key){
        UUID owner=owners.getAuthenticatedOwnerId();
        if(key!=null&&key.length()>100)throw new BacktestExceptions.Invalid("INVALID_IDEMPOTENCY_KEY");
        var today=java.time.LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
        if(request.startDate().isAfter(request.endDate())||request.endDate().isAfter(request.startDate().plusYears(10))||request.endDate().isAfter(today))
            throw new BacktestExceptions.Invalid("INVALID_DATE_RANGE");
        BigDecimal initial=decimal(request.initialCapitalVnd()),risk=decimal(request.riskPerTrancheRate()),aggregate=decimal(request.maxAggregateOpenRiskRate());
        if(aggregate.compareTo(risk)<0)throw new BacktestExceptions.Invalid("AGGREGATE_RISK_BELOW_TRANCHE_RISK");
        CostPolicy cp=request.costs(); boolean excluded=cp.excluded();
        if(excluded && java.util.stream.Stream.of(cp.entryFeeRate(),cp.exitFeeRate(),cp.sellTaxRate(),cp.entrySlippageRate(),cp.exitSlippageRate()).anyMatch(java.util.Objects::nonNull))
            throw new BacktestExceptions.Invalid("INVALID_COST_POLICY");
        if(!excluded && java.util.stream.Stream.of(cp.entryFeeRate(),cp.exitFeeRate(),cp.sellTaxRate(),cp.entrySlippageRate(),cp.exitSlippageRate()).anyMatch(java.util.Objects::isNull))
            throw new BacktestExceptions.Invalid("INCOMPLETE_COST_POLICY");
        BigDecimal zero=BigDecimal.ZERO,ef=excluded?zero:decimal(cp.entryFeeRate()),xf=excluded?zero:decimal(cp.exitFeeRate()),tax=excluded?zero:decimal(cp.sellTaxRate()),es=excluded?zero:decimal(cp.entrySlippageRate()),xs=excluded?zero:decimal(cp.exitSlippageRate());
        if(!excluded&&ef.add(xf).add(tax).add(es).add(xs).signum()==0)throw new BacktestExceptions.Invalid("ZERO_COSTS_REQUIRE_EXCLUSION");
        if(key!=null&&!key.isBlank()){var duplicate=runs.findByOwnerIdAndIdempotencyKey(owner,key);if(duplicate.isPresent()){
            if(!sameRequest(duplicate.get(),request,initial,risk,aggregate,excluded,ef,xf,tax,es,xs))throw new BacktestExceptions.Invalid("IDEMPOTENCY_KEY_REUSED");
            return map(duplicate.get());}}
        var instrument=market.findInstrumentBySymbolIncludingDelisted(request.symbol()).orElseThrow(()->new BacktestExceptions.Invalid("SYMBOL_NOT_FOUND"));
        if(!marketRules.isKnownTradingSession(instrument.venue(),request.startDate())||!marketRules.isKnownTradingSession(instrument.venue(),request.endDate()))
            throw new BacktestExceptions.Invalid("NON_TRADING_DATE");
        if(request.endDate().equals(today)&&market.resolveSession(instrument.venue(),clock.instant()).state()!=com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState.CLOSED)
            throw new BacktestExceptions.Invalid("INCOMPLETE_END_SESSION");
        var assumptions=new com.minhnb.finvera_be.backtest.domain.BacktestTypes.Assumptions(initial,risk,aggregate,new Costs(ef,xf,tax,es,xs,excluded));
        var run=new BacktestRunEntity(UUID.randomUUID(),owner,key==null||key.isBlank()?null:key,request.strategyCode().name(),request.symbol(),instrument.instrumentId(),instrument.venue(),request.startDate(),request.endDate(),assumptions,clock.instant(),clock.instant());
        runs.save(run);events.publishEvent(new RunQueued(run.getId()));return map(run);
    }
    @Transactional
    public void delete(UUID id) {
        UUID owner = owners.getAuthenticatedOwnerId();
        int affected = runs.deleteByIdAndOwnerId(id, owner);
        if (affected == 0) throw new BacktestExceptions.NotFound();
    }
    @Transactional
    public void deleteAll() {
        UUID owner = owners.getAuthenticatedOwnerId();
        runs.deleteAllByOwnerId(owner);
    }
    static RunSummary map(BacktestRunEntity r){return new RunSummary(r.getId(),r.getStatus(),com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode.valueOf(r.getStrategyCode()),r.getSymbol(),r.getReportingStart(),r.getReportingEnd(),r.getProcessedSessions(),r.getTotalSessions(),r.getReasonCode(),r.getCreatedAt(),r.getCompletedAt());}
    private static boolean sameRequest(BacktestRunEntity r,CreateRequest q,BigDecimal initial,BigDecimal risk,BigDecimal aggregate,boolean excluded,BigDecimal ef,BigDecimal xf,BigDecimal tax,BigDecimal es,BigDecimal xs){return r.getStrategyCode().equals(q.strategyCode().name())&&r.getSymbol().equals(q.symbol())&&r.getReportingStart().equals(q.startDate())&&r.getReportingEnd().equals(q.endDate())&&r.getInitialCapitalVnd().compareTo(initial)==0&&r.getRiskPerTrancheRate().compareTo(risk)==0&&r.getMaxAggregateOpenRiskRate().compareTo(aggregate)==0&&r.isCostsExcluded()==excluded&&r.getEntryFeeRate().compareTo(ef)==0&&r.getExitFeeRate().compareTo(xf)==0&&r.getSellTaxRate().compareTo(tax)==0&&r.getEntrySlippageRate().compareTo(es)==0&&r.getExitSlippageRate().compareTo(xs)==0;}
    private static BigDecimal decimal(String x){try{return new BigDecimal(x);}catch(Exception e){throw new BacktestExceptions.Invalid("INVALID_DECIMAL");}}
}
