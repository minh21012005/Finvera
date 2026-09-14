package com.minhnb.finvera_be.backtest.domain;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Sổ cái backtest long-only xác định, xử lý theo thứ tự phiên trong financial-v1. */
public final class BacktestEngineV1 {
    public static final String RULE_VERSION = "backtest-engine-v1";
    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);
    private static final BigDecimal ONE = BigDecimal.ONE;
    private BacktestEngineV1() { }

    public record Signal(boolean triggered, BigDecimal stop, BigDecimal target1, BigDecimal atr14) { }
    public record Session(SessionBar bar, boolean reporting, Signal signal) { }
    public record Trade(int sequence, LocalDate signalDate, LocalDate entryDate, LocalDate exitDate, long quantity,
            BigDecimal rawEntry, BigDecimal effectiveEntry, BigDecimal effectiveExit, BigDecimal entryFee,
            BigDecimal exitFee, BigDecimal sellTax, BigDecimal acquisitionCost, BigDecimal netExitProceeds,
            BigDecimal netPnl, BigDecimal tradeReturn, ExitReason exitReason, java.util.UUID entryBarId,
            java.util.UUID exitBarId, BigDecimal atr14, BigDecimal stop, BigDecimal target) { }
    public record Equity(LocalDate date, BigDecimal cash, BigDecimal positionValue, BigDecimal total,
            short openTranches, BigDecimal dailyReturn, java.util.UUID barId) { }
    public record Event(int sequence, LocalDate signalDate, LocalDate executionDate, EntryOutcome outcome,
            String reasonCode, short openTranches, BigDecimal cash, BigDecimal remainingRisk) { }
    public record Result(List<Trade> trades, List<Equity> equity, List<Event> events, String withheldReason) { }

    private record Pending(LocalDate signalDate, Signal signal, boolean priorSignal) { }
    private static final class Open {
        int seq; LocalDate signalDate, entryDate; long qty; BigDecimal rawEntry, effectiveEntry, entryFee,
                acquisitionCost, acquisitionUnit, stopNet, atr, stop, target; java.util.UUID entryBar;
    }

    public static Result run(List<Session> sessions, Assumptions a, long lotSize) {
        if (sessions == null || sessions.isEmpty()) return new Result(List.of(),List.of(),List.of(),null);
        BigDecimal cash=a.initialCapitalVnd(), previousEquity=null, priorFactor=null;
        boolean priorSignal=false; Pending pending=null; int sequence=0, eventSequence=0;
        List<Open> open=new ArrayList<>(); List<Trade> trades=new ArrayList<>();
        List<Equity> equity=new ArrayList<>(); List<Event> events=new ArrayList<>();
        for (Session session:sessions) {
            SessionBar b=session.bar();
            boolean factorChanged=priorFactor!=null && priorFactor.compareTo(b.adjustmentFactor())!=0;
            if (factorChanged && (pending!=null || !open.isEmpty()))
                return new Result(List.copyOf(trades),List.copyOf(equity),List.copyOf(events),"CORPORATE_ACTION_UNSUPPORTED");
            priorFactor=b.adjustmentFactor();

            for (int i=open.size()-1;i>=0;i--) {
                Open t=open.get(i); ExitFill fill=exitFill(b,t.stop,t.target,a.costs());
                if(fill!=null){ cash=cash.add(close(t,b,fill,a.costs(),trades),MC); open.remove(i); }
            }

            if (pending!=null && session.reporting()) {
                if (b.rawOpen()==null) {
                    events.add(new Event(++eventSequence,pending.signalDate(),b.tradingDate(),EntryOutcome.CANCELLED,
                            "EXECUTION_PRICE_UNAVAILABLE",(short)open.size(),cash,null));
                    pending=null;
                }
            }
            if (pending!=null && session.reporting()) {
                BigDecimal effectiveCandidate=b.rawOpen().multiply(ONE.add(a.costs().entrySlippageRate(),MC),MC);
                if (pending.signal().stop().compareTo(effectiveCandidate)>=0) {
                    events.add(new Event(++eventSequence,pending.signalDate(),b.tradingDate(),EntryOutcome.CANCELLED,
                            "EXECUTION_PRICE_UNAVAILABLE",(short)open.size(),cash,null));
                    pending=null;
                }
            }
            if (pending!=null && session.reporting()) {
                BigDecimal currentPosition=open.stream().map(x->BigDecimal.valueOf(x.qty).multiply(b.rawOpen(),MC))
                        .reduce(BigDecimal.ZERO,(x,y)->x.add(y,MC));
                BigDecimal currentEquity=cash.add(currentPosition,MC);
                BigDecimal effectiveCandidate=b.rawOpen().multiply(ONE.add(a.costs().entrySlippageRate(),MC),MC);
                var pyramid=PyramidingV1.evaluate(pending.priorSignal(),true,
                        open.stream().map(x->x.effectiveEntry).toList(),effectiveCandidate,pending.signal().atr14());
                if(!pyramid.accepted()){
                    events.add(new Event(++eventSequence,pending.signalDate(),b.tradingDate(),EntryOutcome.REJECTED,
                            pyramid.reasonCode(),(short)open.size(),cash,null));
                } else {
                    var risks=open.stream().map(x->new BacktestSizingPolicyV1.OpenRisk(x.qty,x.acquisitionUnit,x.stopNet)).toList();
                    var sized=BacktestSizingPolicyV1.size(currentEquity,cash,b.rawOpen(),pending.signal().stop(),a,lotSize,risks);
                    if(!sized.accepted()) events.add(new Event(++eventSequence,pending.signalDate(),b.tradingDate(),EntryOutcome.REJECTED,
                            sized.reasonCode(),(short)open.size(),cash,sized.remainingRiskVnd()));
                    else {
                        var s=sized.sizing(); Open t=new Open(); t.seq=++sequence;t.signalDate=pending.signalDate();
                        t.entryDate=b.tradingDate();t.qty=s.quantity();t.rawEntry=b.rawOpen();t.effectiveEntry=s.effectiveEntryPriceVnd();
                        t.acquisitionUnit=s.acquisitionUnitCostVnd();t.stopNet=s.stopNetProceedsPerShareVnd();
                        t.acquisitionCost=s.requiredCapitalVnd();t.entryFee=t.acquisitionCost.subtract(t.effectiveEntry.multiply(BigDecimal.valueOf(t.qty),MC),MC);
                        t.stop=pending.signal().stop();t.target=pending.signal().target1();t.atr=pending.signal().atr14();t.entryBar=b.barId();
                        cash=cash.subtract(t.acquisitionCost,MC);open.add(t);
                        ExitFill fill=exitFill(b,t.stop,t.target,a.costs());
                        if(fill!=null){cash=cash.add(close(t,b,fill,a.costs(),trades),MC);open.remove(t);}
                    }
                }
                pending=null;
            }

            if(session.reporting()){
                BigDecimal pv=open.stream().map(x->BigDecimal.valueOf(x.qty).multiply(b.rawClose(),MC))
                        .reduce(BigDecimal.ZERO,(x,y)->x.add(y,MC));
                BigDecimal total=cash.add(pv,MC);
                BigDecimal daily=previousEquity==null?null:total.divide(previousEquity,MC).subtract(ONE,MC);
                equity.add(new Equity(b.tradingDate(),cash,pv,total,(short)open.size(),daily,b.barId())); previousEquity=total;
            }
            Signal sig=session.signal(); boolean current=sig!=null&&sig.triggered();
            if(session.reporting()&&current&&!priorSignal) pending=new Pending(b.tradingDate(),sig,priorSignal);
            priorSignal=current;
        }
        Session last=sessions.get(sessions.size()-1);
        if(pending!=null) events.add(new Event(++eventSequence,pending.signalDate(),null,EntryOutcome.CANCELLED,
                "EXECUTION_PRICE_UNAVAILABLE",(short)open.size(),cash,null));
        for(Open t:new ArrayList<>(open)){
            ExitFill fill=new ExitFill(last.bar().rawClose().multiply(ONE.subtract(a.costs().exitSlippageRate(),MC),MC),ExitReason.END_OF_PERIOD);
            cash=cash.add(close(t,last.bar(),fill,a.costs(),trades),MC);open.remove(t);
        }
        if(last.reporting()&&!equity.isEmpty() && equity.get(equity.size()-1).openTranches()>0){
            Equity old=equity.get(equity.size()-1); BigDecimal daily=equity.size()<2?null:cash.divide(equity.get(equity.size()-2).total(),MC).subtract(ONE,MC);
            equity.set(equity.size()-1,new Equity(old.date(),cash,BigDecimal.ZERO,cash,(short)0,daily,old.barId()));
        }
        return new Result(List.copyOf(trades),List.copyOf(equity),List.copyOf(events),null);
    }

    private record ExitFill(BigDecimal price,ExitReason reason){}
    private static ExitFill exitFill(SessionBar b,BigDecimal stop,BigDecimal target,Costs c){
        BigDecimal m=ONE.subtract(c.exitSlippageRate(),MC);
        if(b.rawOpen()!=null&&b.rawOpen().compareTo(stop)<=0)return new ExitFill(b.rawOpen().multiply(m,MC),ExitReason.STOP_LOSS);
        if(b.rawOpen()!=null&&b.rawOpen().compareTo(target)>=0)return new ExitFill(b.rawOpen().multiply(m,MC),ExitReason.TARGET1);
        if(b.rawLow().compareTo(stop)<=0)return new ExitFill(stop.multiply(m,MC),ExitReason.STOP_LOSS);
        if(b.rawHigh().compareTo(target)>=0)return new ExitFill(target.multiply(m,MC),ExitReason.TARGET1);
        return null;
    }
    private static BigDecimal close(Open t,SessionBar b,ExitFill fill,Costs costs,List<Trade> trades){
        BigDecimal qty=BigDecimal.valueOf(t.qty);
        BigDecimal gross=qty.multiply(fill.price(),MC);
        BigDecimal exitFee=gross.multiply(costs.exitFeeRate(),MC);
        BigDecimal sellTax=gross.multiply(costs.sellTaxRate(),MC);
        BigDecimal net=gross.subtract(exitFee,MC).subtract(sellTax,MC);
        BigDecimal pnl=net.subtract(t.acquisitionCost,MC);
        BigDecimal tradeReturn=pnl.divide(t.acquisitionCost,MC);
        trades.add(new Trade(t.seq,t.signalDate,t.entryDate,b.tradingDate(),t.qty,t.rawEntry,t.effectiveEntry,
                fill.price(),t.entryFee,exitFee,sellTax,t.acquisitionCost,net,pnl,tradeReturn,fill.reason(),
                t.entryBar,b.barId(),t.atr,t.stop,t.target));
        return net;
    }
}
