package com.minhnb.finvera_be.stock.provider.tcbs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.service.LiveStockQuoteService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TcbsStreamStockQuoteProviderTests {

    @Test
    void registersDemandBeforeReadingTheLatestAcceptedQuote() {
        LiveStockQuoteService quotes = mock(LiveStockQuoteService.class);
        when(quotes.findLatest("TCB")).thenReturn(Optional.of(new LiveStockQuoteService.LiveQuote(
                "TCB", new BigDecimal("35.20"), new BigDecimal("34.50"), 10L,
                new BigDecimal("352000"), LocalDate.of(2026, 8, 24),
                Instant.parse("2026-08-24T03:05:30Z"), "TCBS_IFLASH_THESIS")));

        var result = new TcbsStreamStockQuoteProvider(quotes).getQuote("TCB");

        var order = inOrder(quotes);
        order.verify(quotes).ensureSubscribed("TCB");
        order.verify(quotes).findLatest("TCB");
        assertThat(result.lastPrice()).isEqualByComparingTo("35.20");
    }
}
