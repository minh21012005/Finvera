package com.minhnb.finvera_be.stock.provider.tcbs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.provider.MarketDataProvider;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsHttpRestClient;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsRestClient.TickerCommonsItem;
import com.minhnb.finvera_be.market.provider.tcbs.TcbsRestClient.TickerCommonsResponse;
import com.minhnb.finvera_be.stock.provider.StockQuoteProvider.ProviderAuthenticationRequiredException;
import com.minhnb.finvera_be.stock.provider.tcbs.TcbsStockQuoteProvider.QuoteUnavailableException;
import com.minhnb.finvera_be.stock.provider.tcbs.TcbsStockQuoteProvider.SymbolNotReturnedException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Sanitized, hardcoded fixture values only (G-03 evidence: 2026-08-22 owner-run probe against
 * VNM,TCB,HPG confirmed this exact field shape for {@code tickers=}). No live network.
 */
class TcbsStockQuoteProviderTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-22T04:00:00Z"), ZoneOffset.UTC);

    @Test
    void mapsMatchPriceRefPriceVolumeAndValueFromTheReturnedItem() {
        TcbsHttpRestClient restClient = mock(TcbsHttpRestClient.class);
        when(restClient.fetchTickerCommonsForSymbols(List.of("VNM"))).thenReturn(sanitizedResponse("VNM",
                "68500.000000", "68000.000000", "1200000", "82200000000000"));
        TcbsStockQuoteProvider provider = new TcbsStockQuoteProvider(restClient, FIXED_CLOCK);

        var quote = provider.getQuote("vnm"); // lowercase input must still resolve

        assertThat(quote.symbol()).isEqualTo("VNM");
        assertThat(quote.lastPrice()).isEqualByComparingTo("68500.000000");
        assertThat(quote.officialReferencePrice()).isEqualByComparingTo("68000.000000");
        assertThat(quote.sessionVolume()).isEqualTo(1_200_000L);
        assertThat(quote.sessionValueVnd()).isEqualByComparingTo("82200000000000");
        assertThat(quote.observedAt()).isEqualTo(FIXED_CLOCK.instant());
        assertThat(quote.sessionIndication()).isEqualTo("UNKNOWN");
    }

    @Test
    void throwsSymbolNotReturnedWhenTcbsOmitsTheRequestedSymbol() {
        TcbsHttpRestClient restClient = mock(TcbsHttpRestClient.class);
        when(restClient.fetchTickerCommonsForSymbols(List.of("ZZZZ")))
                .thenReturn(new TickerCommonsResponse("2026-08-22", List.of()));
        TcbsStockQuoteProvider provider = new TcbsStockQuoteProvider(restClient, FIXED_CLOCK);

        assertThatThrownBy(() -> provider.getQuote("ZZZZ")).isInstanceOf(SymbolNotReturnedException.class);
    }

    @Test
    void throwsQuoteUnavailableWhenMatchPriceIsMissingRatherThanZeroFilling() {
        TcbsHttpRestClient restClient = mock(TcbsHttpRestClient.class);
        when(restClient.fetchTickerCommonsForSymbols(List.of("VNM"))).thenReturn(new TickerCommonsResponse(
                "2026-08-22", List.of(new TickerCommonsItem("VNM", 0, null, null, null,
                        "1200000", "82200000000000", null, null, null, "68000.000000"))));
        TcbsStockQuoteProvider provider = new TcbsStockQuoteProvider(restClient, FIXED_CLOCK);

        assertThatThrownBy(() -> provider.getQuote("VNM")).isInstanceOf(QuoteUnavailableException.class);
    }

    @Test
    void translatesTheMarketModuleAuthExceptionIntoTheStockModuleOne() {
        TcbsHttpRestClient restClient = mock(TcbsHttpRestClient.class);
        when(restClient.fetchTickerCommonsForSymbols(List.of("VNM")))
                .thenThrow(new MarketDataProvider.ProviderAuthenticationRequiredException());
        TcbsStockQuoteProvider provider = new TcbsStockQuoteProvider(restClient, FIXED_CLOCK);

        assertThatThrownBy(() -> provider.getQuote("VNM"))
                .isInstanceOf(ProviderAuthenticationRequiredException.class);
    }

    @Test
    void fetchCurrentBarUsesRealOpenHighLowFromTheResponseNotDerivedFromClose() {
        TcbsHttpRestClient restClient = mock(TcbsHttpRestClient.class);
        when(restClient.fetchTickerCommonsForSymbols(List.of("VNM"))).thenReturn(sanitizedResponse(
                "VNM", "68500.000000", "68000.000000", "1200000", "82200000000000"));
        TcbsStockQuoteProvider provider = new TcbsStockQuoteProvider(restClient, FIXED_CLOCK);

        var bar = provider.fetchCurrentBar("VNM", LocalDate.of(2026, 8, 22));

        assertThat(bar.symbol()).isEqualTo("VNM");
        assertThat(bar.tradingDate()).isEqualTo(LocalDate.of(2026, 8, 22));
        assertThat(bar.close()).isEqualByComparingTo("68500.000000");
        assertThat(bar.open()).isEqualByComparingTo("68200.000000");
        assertThat(bar.high()).isEqualByComparingTo("68900.000000");
        assertThat(bar.low()).isEqualByComparingTo("67800.000000");
        assertThat(bar.volume()).isEqualTo(1_200_000L);
    }

    @Test
    void fetchCurrentBarFallsBackToCloseOnlyWhenTcbsOmitsOpenHighLow() {
        TcbsHttpRestClient restClient = mock(TcbsHttpRestClient.class);
        when(restClient.fetchTickerCommonsForSymbols(List.of("VNM"))).thenReturn(new TickerCommonsResponse(
                "2026-08-22", List.of(new TickerCommonsItem("VNM", 0, "68500.000000", null, null,
                        "1200000", "82200000000000", null, null, null, "68000.000000"))));
        TcbsStockQuoteProvider provider = new TcbsStockQuoteProvider(restClient, FIXED_CLOCK);

        var bar = provider.fetchCurrentBar("VNM", LocalDate.of(2026, 8, 22));

        assertThat(bar.open()).isEqualByComparingTo(bar.close());
        assertThat(bar.high()).isEqualByComparingTo(bar.close());
        assertThat(bar.low()).isEqualByComparingTo(bar.close());
    }

    private static TickerCommonsResponse sanitizedResponse(
            String symbol, String matchPrice, String refPrice, String totalVol, String totalVal) {
        return new TickerCommonsResponse("2026-08-22", List.of(new TickerCommonsItem(
                symbol, 0, matchPrice, "500.000000", "0.73", totalVol, totalVal,
                "68200.000000", "68900.000000", "67800.000000", refPrice)));
    }
}
