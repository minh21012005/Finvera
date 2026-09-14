package com.minhnb.finvera_be.backtest.service;

import com.minhnb.finvera_be.backtest.domain.*;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.*;
import com.minhnb.finvera_be.backtest.entity.*;
import com.minhnb.finvera_be.backtest.entity.BacktestResultEntities.*;
import com.minhnb.finvera_be.backtest.repository.*;
import com.minhnb.finvera_be.market.service.BacktestMarketRuleService;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.EntryStatus;
import com.minhnb.finvera_be.stock.service.BacktestHistoryDataService;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BacktestExecutionService {
    private final BacktestRunRepository runs; private final BacktestTradeRepository trades;
    private final BacktestEquityPointRepository equity; private final BacktestMetricRepository metrics;
    private final BacktestEntryEventRepository events; private final BacktestEvidenceRepository evidence;
    private final BacktestHistoryDataService history; private final BacktestMarketRuleService market; private final Clock clock;
    public BacktestExecutionService(BacktestRunRepository runs,BacktestTradeRepository trades,BacktestEquityPointRepository equity,
            BacktestMetricRepository metrics,BacktestEntryEventRepository events,BacktestEvidenceRepository evidence,
            BacktestHistoryDataService history,BacktestMarketRuleService market,Clock clock){this.runs=runs;this.trades=trades;this.equity=equity;
        this.metrics=metrics;this.events=events;this.evidence=evidence;this.history=history;this.market=market;this.clock=clock;}

    @Transactional
    public void execute(UUID runId){
        BacktestRunEntity run=runs.findById(runId).orElseThrow();
        if(run.getStatus()!=RunStatus.RUNNING)return;
        var snapshot=history.load(run.getInstrumentId(),StrategyCode.valueOf(run.getStrategyCode()),run.getReportingStart(),run.getReportingEnd(),run.getDataCutoffAcceptedAt());
        if(snapshot.unavailableReason()!=null){run.withhold(snapshot.unavailableReason(),clock.instant());return;}
        int total=(int)snapshot.sessions().stream().filter(BacktestHistoryDataService.HistoricalSession::reporting).count();
        run.progress(0,total,clock.instant());
        var assumptions=new Assumptions(run.getInitialCapitalVnd(),run.getRiskPerTrancheRate(),run.getMaxAggregateOpenRiskRate(),
                new Costs(run.getEntryFeeRate(),run.getExitFeeRate(),run.getSellTaxRate(),run.getEntrySlippageRate(),run.getExitSlippageRate(),run.isCostsExcluded()));
        long lot=market.rules(run.getVenue(),run.getReportingStart()).lotSize();
        try {
            if(snapshot.sessions().stream().filter(BacktestHistoryDataService.HistoricalSession::reporting)
                    .anyMatch(s->!market.isKnownTradingSession(run.getVenue(),s.bar().tradingDate())))
                throw new Withheld("INCOHERENT_TRADING_CALENDAR");
            List<BacktestEngineV1.Session> sessions=snapshot.sessions().stream().map(s->{var x=s.evaluation();
                if(x.status()==EntryStatus.WITHHELD)throw new Withheld(x.reasonCode());
                var signal=x.status()==EntryStatus.SIGNAL?new BacktestEngineV1.Signal(true,x.levels().stopLoss(),x.levels().target1(),
                        x.levels().entryHigh().subtract(x.levels().entryLow()).multiply(new java.math.BigDecimal("2"))):new BacktestEngineV1.Signal(false,null,null,null);
                return new BacktestEngineV1.Session(s.bar(),s.reporting(),signal);}).toList();
            var result=BacktestEngineV1.run(sessions,assumptions,lot);
            if(result.withheldReason()!=null){run.withhold(result.withheldReason(),clock.instant());return;}
            trades.saveAll(result.trades().stream().map(t->new BacktestTradeEntity(UUID.randomUUID(),runId,t.sequence(),t.signalDate(),t.entryDate(),t.exitDate(),run.getStrategyCode(),t.atr14(),t.stop(),t.target(),t.quantity(),lot,t.rawEntry(),t.effectiveEntry(),t.effectiveExit(),t.entryFee(),t.exitFee(),t.sellTax(),t.acquisitionCost(),t.netExitProceeds(),t.netPnl(),t.tradeReturn(),t.exitReason(),t.entryBarId(),t.exitBarId())).toList());
            equity.saveAll(result.equity().stream().map(p->new BacktestEquityPointEntity(UUID.randomUUID(),runId,p.date(),p.cash(),p.positionValue(),p.total(),p.openTranches(),p.dailyReturn(),p.barId())).toList());
            events.saveAll(result.events().stream().map(e->new EntryEvent(UUID.randomUUID(),runId,e.sequence(),e.signalDate(),e.executionDate(),e.outcome(),e.reasonCode(),e.openTranches(),e.cash(),e.remainingRisk())).toList());
            metrics.saveAll(BacktestMetricsV1.calculate(run.getInitialCapitalVnd(),result.equity(),result.trades()).stream().map(m->new Metric(UUID.randomUUID(),runId,m.code(),m.value(),m.unit(),m.availability(),m.reasonCode())).toList());
            evidence.saveAll(List.of(new Evidence(UUID.randomUUID(),runId,"inputFingerprint",snapshot.fingerprint(),EvidenceUnit.TEXT),
                    new Evidence(UUID.randomUUID(),runId,"dataCutoffAcceptedAt",run.getDataCutoffAcceptedAt().toString(),EvidenceUnit.INSTANT),
                    new Evidence(UUID.randomUUID(),runId,"priceAdjustmentBasis",snapshot.adjustmentBasis(),EvidenceUnit.TEXT),
                    new Evidence(UUID.randomUUID(),runId,"marketDataSources",String.join(",",snapshot.sources()),EvidenceUnit.TEXT),
                    new Evidence(UUID.randomUUID(),runId,"lotRuleVersion",run.getLotRuleVersion(),EvidenceUnit.TEXT),
                    new Evidence(UUID.randomUUID(),runId,"lotRuleBasis","CURRENT_RULE_APPLIED_TO_ALL_SESSIONS",EvidenceUnit.TEXT)));
            run.complete(snapshot.fingerprint(),total,clock.instant());
        } catch(Withheld x){run.withhold(x.reason,clock.instant());}
    }
    private static final class Withheld extends RuntimeException{final String reason;Withheld(String reason){this.reason=reason;}}
}
