package com.minhnb.finvera_be.backtest.service;
public final class BacktestExceptions {
    private BacktestExceptions(){}
    public static class Invalid extends RuntimeException { public Invalid(String reason){super(reason);} }
    public static class NotFound extends RuntimeException { public NotFound(){super("BACKTEST_NOT_FOUND");} }
    public static class ResultUnavailable extends RuntimeException { public ResultUnavailable(){super("BACKTEST_RESULT_NOT_AVAILABLE");} }
}
