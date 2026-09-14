package com.minhnb.finvera_be.backtest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name="backtest_equity_point")
public class BacktestEquityPointEntity {
    @Id private UUID id;
    @Column(name="run_id",nullable=false) private UUID runId;
    @Column(name="trading_date",nullable=false) private LocalDate tradingDate;
    @Column(name="cash_vnd",nullable=false,precision=34,scale=12) private BigDecimal cashVnd;
    @Column(name="open_position_value_vnd",nullable=false,precision=34,scale=12) private BigDecimal openPositionValueVnd;
    @Column(name="total_equity_vnd",nullable=false,precision=34,scale=12) private BigDecimal totalEquityVnd;
    @Column(name="open_tranche_count",nullable=false) private short openTrancheCount;
    @Column(name="daily_return_rate",precision=34,scale=8) private BigDecimal dailyReturnRate;
    @Column(name="daily_bar_id",nullable=false) private UUID dailyBarId;

    protected BacktestEquityPointEntity() { }
    public BacktestEquityPointEntity(UUID id,UUID runId,LocalDate tradingDate,BigDecimal cashVnd,
            BigDecimal openPositionValueVnd,BigDecimal totalEquityVnd,short openTrancheCount,
            BigDecimal dailyReturnRate,UUID dailyBarId){
        this.id=id;this.runId=runId;this.tradingDate=tradingDate;this.cashVnd=cashVnd;
        this.openPositionValueVnd=openPositionValueVnd;this.totalEquityVnd=totalEquityVnd;
        this.openTrancheCount=openTrancheCount;this.dailyReturnRate=dailyReturnRate;this.dailyBarId=dailyBarId;
    }
    public UUID getId(){return id;} public UUID getRunId(){return runId;}
    public LocalDate getTradingDate(){return tradingDate;} public BigDecimal getCashVnd(){return cashVnd;}
    public BigDecimal getOpenPositionValueVnd(){return openPositionValueVnd;}
    public BigDecimal getTotalEquityVnd(){return totalEquityVnd;}
    public short getOpenTrancheCount(){return openTrancheCount;} public BigDecimal getDailyReturnRate(){return dailyReturnRate;}
    public UUID getDailyBarId(){return dailyBarId;}
}
