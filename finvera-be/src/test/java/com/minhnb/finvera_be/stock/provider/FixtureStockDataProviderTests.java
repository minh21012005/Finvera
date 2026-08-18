package com.minhnb.finvera_be.stock.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhnb.finvera_be.stock.provider.CorporateActionProvider.CorporateAction;
import com.minhnb.finvera_be.stock.provider.FundamentalReportProvider.FundamentalReport;
import com.minhnb.finvera_be.stock.provider.StockHistoryProvider.DailyBar;
import com.minhnb.finvera_be.stock.provider.StockQuoteProvider.ProviderAuthenticationRequiredException;
import com.minhnb.finvera_be.stock.provider.StockReferenceProvider.InstrumentNotFoundException;
import com.minhnb.finvera_be.stock.provider.fixture.FixtureCorporateActionProvider;
import com.minhnb.finvera_be.stock.provider.fixture.FixtureFundamentalReportProvider;
import com.minhnb.finvera_be.stock.provider.fixture.FixtureStockHistoryProvider;
import com.minhnb.finvera_be.stock.provider.fixture.FixtureStockQuoteProvider;
import com.minhnb.finvera_be.stock.provider.fixture.FixtureStockReferenceProvider;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the five read-only ports (contracts/stock-data-provider.md).
 * These assert port isolation, allowlisted fixture mapping, degraded/auth-required
 * states, and that no adapter exposes a non-read-only operation. Acceptance checks
 * A-1 to A-11 are enforced by the ingestion service (T017-T018), not the provider
 * ports themselves, mirroring Feature 001's MarketIngestionService boundary.
 */
class FixtureStockDataProviderTests {

    @Test
    void referenceProviderResolvesAnAllowlistedSymbolAndRejectsAnUnknownOne() {
        StockReferenceProvider provider = new FixtureStockReferenceProvider();

        var reference = provider.findInstrument("FPT");
        assertThat(reference.symbol()).isEqualTo("FPT");
        assertThat(reference.venue()).isEqualTo("HOSE");
        assertThat(reference.sharesOutstanding()).isPositive();

        assertThatThrownBy(() -> provider.findInstrument("ZZZZZ"))
                .isInstanceOf(InstrumentNotFoundException.class);
    }

    @Test
    void referenceProviderNeverExposesAWriteOrTradingOperation() {
        for (var method : StockReferenceProvider.class.getMethods()) {
            String name = method.getName();
            assertThat(name).doesNotContainIgnoringCase("order")
                    .doesNotContainIgnoringCase("trade")
                    .doesNotContainIgnoringCase("cash")
                    .doesNotContainIgnoringCase("account");
        }
    }

    @Test
    void quoteProviderReadsEachAllowlistedFreshnessScenario() {
        var complete = new FixtureStockQuoteProvider(FixtureStockQuoteProvider.FixtureScenario.COMPLETE)
                .getQuote("FPT");
        assertThat(complete.lastPrice()).isNotNull();
        assertThat(complete.sessionIndication()).isEqualTo("OPEN");

        var missingReference = new FixtureStockQuoteProvider(
                FixtureStockQuoteProvider.FixtureScenario.MISSING_REFERENCE).getQuote("FPT");
        assertThat(missingReference.officialReferencePrice()).isNull();
        assertThat(missingReference.lastPrice()).isNotNull();
    }

    @Test
    void quoteProviderThrowsWhenProviderAuthenticationIsRequired() {
        var provider = new FixtureStockQuoteProvider(FixtureStockQuoteProvider.FixtureScenario.AUTH_REQUIRED);
        assertThatThrownBy(() -> provider.getQuote("FPT"))
                .isInstanceOf(ProviderAuthenticationRequiredException.class);
    }

    @Test
    void quoteProviderNeverExposesAWriteOrTradingOperation() {
        for (var method : StockQuoteProvider.class.getMethods()) {
            assertThat(method.getName()).doesNotContainIgnoringCase("order")
                    .doesNotContainIgnoringCase("trade")
                    .doesNotContainIgnoringCase("cash");
        }
    }

    @Test
    void historyProviderReturnsExactlyTheRequestedTradingDateWindow() {
        StockHistoryProvider provider = new FixtureStockHistoryProvider(
                FixtureStockHistoryProvider.FixtureScenario.GOLDEN_250);
        List<DailyBar> bars = provider.getDailyBars("FPT", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 14));
        assertThat(bars).isNotEmpty();
        assertThat(bars).allMatch(bar -> !bar.tradingDate().isBefore(LocalDate.of(2026, 8, 1))
                && !bar.tradingDate().isAfter(LocalDate.of(2026, 8, 14)));
        assertThat(bars).allMatch(bar -> "ADJUSTED".equals(bar.adjustmentIndication()));
    }

    @Test
    void historyProviderRejectsAnInvertedDateRange() {
        StockHistoryProvider provider = new FixtureStockHistoryProvider(
                FixtureStockHistoryProvider.FixtureScenario.GOLDEN_250);
        assertThatThrownBy(() -> provider.getDailyBars("FPT", LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void corporateActionProviderReturnsTheSplitOnlyWhenItFallsInsideTheRequestedWindow() {
        CorporateActionProvider provider = new FixtureCorporateActionProvider(
                FixtureCorporateActionProvider.FixtureScenario.SPLIT_IN_WINDOW);
        List<CorporateAction> inWindow = provider.getCorporateActions(
                "SPLIT", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 1));
        assertThat(inWindow).hasSize(1);
        assertThat(inWindow.getFirst().actionType()).isEqualTo("SPLIT");

        List<CorporateAction> outsideWindow = provider.getCorporateActions(
                "SPLIT", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31));
        assertThat(outsideWindow).isEmpty();
    }

    @Test
    void fundamentalReportProviderReturnsSourceLineItemsBeforeCatalogMapping() {
        FundamentalReportProvider provider = new FixtureFundamentalReportProvider(
                FixtureFundamentalReportProvider.FixtureScenario.COMPLETE);
        List<FundamentalReport> reports = provider.getReports("FPT", "QUARTER", null, null);
        assertThat(reports).hasSize(1);
        assertThat(reports.getFirst().lineItems()).isNotEmpty();
        assertThat(reports.getFirst().periodType()).isEqualTo("QUARTER");
    }

    @Test
    void fundamentalReportProviderReturnsBothRevisionsForARestatedPeriod() {
        FundamentalReportProvider provider = new FixtureFundamentalReportProvider(
                FixtureFundamentalReportProvider.FixtureScenario.RESTATED);
        List<FundamentalReport> reports = provider.getReports("FPT", "QUARTER", null, null);
        assertThat(reports).hasSize(2);
    }

    @Test
    void fundamentalReportProviderNeverExposesAWriteOrTradingOperation() {
        for (var method : FundamentalReportProvider.class.getMethods()) {
            assertThat(method.getName()).doesNotContainIgnoringCase("order")
                    .doesNotContainIgnoringCase("trade")
                    .doesNotContainIgnoringCase("cash")
                    .doesNotContainIgnoringCase("account");
        }
    }
}
