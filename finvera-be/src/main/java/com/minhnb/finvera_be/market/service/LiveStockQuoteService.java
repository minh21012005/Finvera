package com.minhnb.finvera_be.market.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/** Explicit application API through which the stock module reads accepted live quotes. */
public interface LiveStockQuoteService {
    void ensureSubscribed(String symbol);
    Optional<LiveQuote> findLatest(String symbol);
    record LiveQuote(String symbol, BigDecimal lastPrice, BigDecimal referencePrice,
            Long sessionVolume, BigDecimal sessionValueVnd, LocalDate tradingDate,
            Instant observedAt, String source) { }
}
