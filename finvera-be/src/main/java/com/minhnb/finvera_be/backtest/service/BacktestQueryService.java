package com.minhnb.finvera_be.backtest.service;

import static com.minhnb.finvera_be.backtest.dto.BacktestDtos.*;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.RunStatus;
import com.minhnb.finvera_be.backtest.entity.*;
import com.minhnb.finvera_be.backtest.repository.*;
import com.minhnb.finvera_be.portfolio.service.OwnerScopedAccess;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class BacktestQueryService {
    private final BacktestRunRepository runs;private final BacktestTradeRepository trades;private final BacktestEquityPointRepository equity;
    private final BacktestMetricRepository metrics;private final BacktestEntryEventRepository events;private final BacktestEvidenceRepository evidence;private final OwnerScopedAccess owners;
    public BacktestQueryService(BacktestRunRepository runs,BacktestTradeRepository trades,BacktestEquityPointRepository equity,BacktestMetricRepository metrics,BacktestEntryEventRepository events,BacktestEvidenceRepository evidence,OwnerScopedAccess owners){this.runs=runs;this.trades=trades;this.equity=equity;this.metrics=metrics;this.events=events;this.evidence=evidence;this.owners=owners;}
    public Page<RunSummary> list(int limit,int offset){var p=runs.findAllByOwnerIdOrderByCreatedAtDescIdDesc(owners.getAuthenticatedOwnerId(),PageRequest.of(offset/limit,limit));return new Page<>(p.stream().map(BacktestCommandService::map).toList(),p.getTotalElements(),limit,offset);}
    public RunDetail detail(UUID id){var r=owned(id);var summary=BacktestCommandService.map(r);var costs=new CostPolicy(r.isCostsExcluded(),r.isCostsExcluded()?null:s(r.getEntryFeeRate()),r.isCostsExcluded()?null:s(r.getExitFeeRate()),r.isCostsExcluded()?null:s(r.getSellTaxRate()),r.isCostsExcluded()?null:s(r.getEntrySlippageRate()),r.isCostsExcluded()?null:s(r.getExitSlippageRate()));
        var assumptions=new Assumptions(s(r.getInitialCapitalVnd()),s(r.getRiskPerTrancheRate()),s(r.getMaxAggregateOpenRiskRate()),costs,r.getStrategyRuleVersion(),r.getSizingRuleVersion(),r.getEngineRuleVersion(),r.getMetricsRuleVersion(),r.getPyramidingRuleVersion(),"NEXT_ELIGIBLE_SESSION_OPEN","STOP_FIRST",4,"0.5");
        var evidenceRows=evidence.findAllByRunIdOrderByKey(id);
        var warnings=new ArrayList<String>();if(r.isCostsExcluded())warnings.add("COSTS_EXCLUDED");
        warnings.add("CURRENT_MARKET_LOT_APPLIED_HISTORICALLY");warnings.add("SURVIVORSHIP_BIAS_NOT_ELIMINATED");
        warnings.add("SUSPENSION_DELISTING_COVERAGE_LIMITED");
        if(evidenceRows.stream().anyMatch(e->e.getKey().equals("priceAdjustmentBasis")&&e.getValue().equals("PROVIDER_ADJUSTED")))
            warnings.add("PROVIDER_ADJUSTED_EXECUTION_BASIS");
        return new RunDetail(summary.id(),summary.status(),summary.strategyCode(),summary.symbol(),summary.startDate(),summary.endDate(),summary.processedSessions(),summary.totalSessions(),summary.reasonCode(),summary.createdAt(),summary.completedAt(),r.getDataCutoffAcceptedAt(),assumptions,metrics.findAllByRunIdOrderByCode(id).stream().map(m->new Metric(m.getCode(),m.getValue()==null?null:s(m.getValue()),m.getUnit(),m.getAvailability(),m.getReasonCode(),m.getRuleVersion())).toList(),evidenceRows.stream().map(e->new Evidence(e.getKey(),e.getValue(),e.getUnit())).toList(),List.copyOf(warnings));}
    public Page<Trade> trades(UUID id,int limit,int offset){terminal(owned(id));var p=trades.findAllByRunIdOrderBySequenceNo(id,PageRequest.of(offset/limit,limit));return new Page<>(p.stream().map(t->new Trade(t.getSequenceNo(),t.getSignalDate(),t.getEntryDate(),t.getExitDate(),t.getQuantity(),s(t.getRawEntryPriceVnd()),s(t.getEffectiveEntryPriceVnd()),s(t.getEffectiveExitPriceVnd()),s(t.getAcquisitionCostVnd()),s(t.getNetExitProceedsVnd()),s(t.getNetPnlVnd()),s(t.getTradeReturnRate()),t.getExitReason())).toList(),p.getTotalElements(),limit,offset);}
    public Page<EquityPoint> equity(UUID id,int limit,int offset){terminal(owned(id));var p=equity.findAllByRunIdOrderByTradingDate(id,PageRequest.of(offset/limit,limit));return new Page<>(p.stream().map(x->new EquityPoint(x.getTradingDate(),s(x.getCashVnd()),s(x.getOpenPositionValueVnd()),s(x.getTotalEquityVnd()),x.getOpenTrancheCount(),x.getDailyReturnRate()==null?null:s(x.getDailyReturnRate()))).toList(),p.getTotalElements(),limit,offset);}
    public Page<EntryEvent> events(UUID id,int limit,int offset){terminal(owned(id));var p=events.findAllByRunIdOrderBySequenceNo(id,PageRequest.of(offset/limit,limit));return new Page<>(p.stream().map(x->new EntryEvent(x.getSequenceNo(),x.getSignalDate(),x.getExecutionDate(),x.getOutcome(),x.getReasonCode(),x.getOpenTrancheCount())).toList(),p.getTotalElements(),limit,offset);}
    private BacktestRunEntity owned(UUID id){return runs.findByIdAndOwnerId(id,owners.getAuthenticatedOwnerId()).orElseThrow(BacktestExceptions.NotFound::new);}private static void terminal(BacktestRunEntity r){if(r.getStatus()==RunStatus.QUEUED||r.getStatus()==RunStatus.RUNNING)throw new BacktestExceptions.ResultUnavailable();}private static String s(java.math.BigDecimal x){return x.stripTrailingZeros().toPlainString();}
}
