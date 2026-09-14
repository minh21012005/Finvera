package com.minhnb.finvera_be.backtest.entity;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.ExitReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "backtest_trade")
public class BacktestTradeEntity {
    @Id private UUID id;
    @Column(name="run_id",nullable=false) private UUID runId;
    @Column(name="sequence_no",nullable=false) private int sequenceNo;
    @Column(name="signal_date",nullable=false) private LocalDate signalDate;
    @Column(name="entry_date",nullable=false) private LocalDate entryDate;
    @Column(name="exit_date",nullable=false) private LocalDate exitDate;
    @Column(name="strategy_code",nullable=false,length=32) private String strategyCode;
    @Column(name="strategy_rule_version",nullable=false,length=40) private String strategyRuleVersion;
    @Column(name="signal_atr14_vnd",nullable=false,precision=34,scale=12) private BigDecimal signalAtr14Vnd;
    @Column(name="stop_price_vnd",nullable=false,precision=34,scale=12) private BigDecimal stopPriceVnd;
    @Column(name="target_price_vnd",nullable=false,precision=34,scale=12) private BigDecimal targetPriceVnd;
    @Column(nullable=false) private long quantity;
    @Column(name="lot_size",nullable=false) private long lotSize;
    @Column(name="raw_entry_price_vnd",nullable=false,precision=34,scale=12) private BigDecimal rawEntryPriceVnd;
    @Column(name="effective_entry_price_vnd",nullable=false,precision=34,scale=12) private BigDecimal effectiveEntryPriceVnd;
    @Column(name="effective_exit_price_vnd",nullable=false,precision=34,scale=12) private BigDecimal effectiveExitPriceVnd;
    @Column(name="entry_fee_vnd",nullable=false,precision=34,scale=12) private BigDecimal entryFeeVnd;
    @Column(name="exit_fee_vnd",nullable=false,precision=34,scale=12) private BigDecimal exitFeeVnd;
    @Column(name="sell_tax_vnd",nullable=false,precision=34,scale=12) private BigDecimal sellTaxVnd;
    @Column(name="acquisition_cost_vnd",nullable=false,precision=34,scale=12) private BigDecimal acquisitionCostVnd;
    @Column(name="net_exit_proceeds_vnd",nullable=false,precision=34,scale=12) private BigDecimal netExitProceedsVnd;
    @Column(name="net_pnl_vnd",nullable=false,precision=34,scale=12) private BigDecimal netPnlVnd;
    @Column(name="trade_return_rate",nullable=false,precision=34,scale=8) private BigDecimal tradeReturnRate;
    @Enumerated(EnumType.STRING) @Column(name="exit_reason",nullable=false,length=24) private ExitReason exitReason;
    @Column(name="entry_bar_id",nullable=false) private UUID entryBarId;
    @Column(name="exit_bar_id",nullable=false) private UUID exitBarId;

    protected BacktestTradeEntity() { }

    public BacktestTradeEntity(UUID id, UUID runId, int sequenceNo, LocalDate signalDate,
            LocalDate entryDate, LocalDate exitDate, String strategyCode, BigDecimal signalAtr14Vnd,
            BigDecimal stopPriceVnd, BigDecimal targetPriceVnd, long quantity, long lotSize,
            BigDecimal rawEntryPriceVnd, BigDecimal effectiveEntryPriceVnd,
            BigDecimal effectiveExitPriceVnd, BigDecimal entryFeeVnd, BigDecimal exitFeeVnd,
            BigDecimal sellTaxVnd, BigDecimal acquisitionCostVnd, BigDecimal netExitProceedsVnd,
            BigDecimal netPnlVnd, BigDecimal tradeReturnRate, ExitReason exitReason,
            UUID entryBarId, UUID exitBarId) {
        this.id=id; this.runId=runId; this.sequenceNo=sequenceNo; this.signalDate=signalDate;
        this.entryDate=entryDate; this.exitDate=exitDate; this.strategyCode=strategyCode;
        this.strategyRuleVersion="strategy-signal-v1"; this.signalAtr14Vnd=signalAtr14Vnd;
        this.stopPriceVnd=stopPriceVnd; this.targetPriceVnd=targetPriceVnd;
        this.quantity=quantity; this.lotSize=lotSize; this.rawEntryPriceVnd=rawEntryPriceVnd;
        this.effectiveEntryPriceVnd=effectiveEntryPriceVnd; this.effectiveExitPriceVnd=effectiveExitPriceVnd;
        this.entryFeeVnd=entryFeeVnd; this.exitFeeVnd=exitFeeVnd; this.sellTaxVnd=sellTaxVnd;
        this.acquisitionCostVnd=acquisitionCostVnd; this.netExitProceedsVnd=netExitProceedsVnd;
        this.netPnlVnd=netPnlVnd; this.tradeReturnRate=tradeReturnRate; this.exitReason=exitReason;
        this.entryBarId=entryBarId; this.exitBarId=exitBarId;
    }

    public UUID getId(){return id;} public UUID getRunId(){return runId;} public int getSequenceNo(){return sequenceNo;}
    public LocalDate getSignalDate(){return signalDate;} public LocalDate getEntryDate(){return entryDate;}
    public LocalDate getExitDate(){return exitDate;} public long getQuantity(){return quantity;}
    public BigDecimal getNetPnlVnd(){return netPnlVnd;} public BigDecimal getTradeReturnRate(){return tradeReturnRate;}
    public ExitReason getExitReason(){return exitReason;}
    public BigDecimal getRawEntryPriceVnd(){return rawEntryPriceVnd;} public BigDecimal getEffectiveEntryPriceVnd(){return effectiveEntryPriceVnd;}
    public BigDecimal getEffectiveExitPriceVnd(){return effectiveExitPriceVnd;} public BigDecimal getAcquisitionCostVnd(){return acquisitionCostVnd;}
    public BigDecimal getNetExitProceedsVnd(){return netExitProceedsVnd;}
}
