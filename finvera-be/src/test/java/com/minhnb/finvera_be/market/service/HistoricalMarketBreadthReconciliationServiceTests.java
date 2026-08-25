package com.minhnb.finvera_be.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.domain.breadth.BreadthCalculator;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.stock.service.StockReferenceDataService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HistoricalMarketBreadthReconciliationServiceTests {
    @Mock MarketInstrumentRepository instruments;
    @Mock StockReferenceDataService stockReferenceData;
    @Mock BreadthService breadth;
    @Mock LiveMarketRegimeReconciliationService regimes;

    @Test
    void rebuildsLatestCompletedBreadthFromAcceptedStockDailyBarsAndTriggersRegimeRepair() {
        LocalDate previousDate = LocalDate.of(2026, 8, 23);
        LocalDate currentDate = LocalDate.of(2026, 8, 24);
        UUID fpt = UUID.randomUUID();
        UUID vnm = UUID.randomUUID();
        UUID vic = UUID.randomUUID();
        when(instruments.findByListedToIsNullAndInstrumentTypeAndStatusOrderByVenueAscSymbolAsc(
                "COMMON_EQUITY", "ACTIVE"))
                .thenReturn(List.of(instrument(fpt, "HOSE", "FPT"), instrument(vnm, "HOSE", "VNM"),
                        instrument(vic, "HOSE", "VIC")));
        when(stockReferenceData.findLatestDailyBars(List.of(fpt, vnm, vic), 2)).thenReturn(List.of(
                bar(fpt, previousDate, "105000.000000", null, "2026-08-23T08:00:00Z"),
                bar(fpt, currentDate, "101000.000000", "100000.000000", "2026-08-24T08:00:00Z"),
                bar(vnm, previousDate, "60000.000000", "2026-08-23T08:00:00Z"),
                bar(vnm, currentDate, "59000.000000", "2026-08-24T08:01:00Z"),
                bar(vic, currentDate, "110000.000000", "2026-08-24T08:02:00Z")));
        when(breadth.latestFor(currentDate)).thenReturn(Optional.empty());
        var persisted = new BreadthService.Snapshot(UUID.randomUUID(), currentDate,
                Instant.parse("2026-08-24T08:02:00Z"), DataStatus.PARTIAL,
                "EOD",
                new BreadthCalculator.Result(1, 1, 0, 1, 3, List.of("MISSING_REFERENCE_PRICE")),
                "breadth-universe-v1", "a".repeat(64));
        when(breadth.persist(any(), any(), any(), any(), any(), any())).thenReturn(persisted);
        var service = new HistoricalMarketBreadthReconciliationService(
                instruments, stockReferenceData, breadth, regimes);

        var result = service.reconcileLatestCompletedSession();

        assertThat(result.status()).isEqualTo("APPLIED");
        assertThat(result.tradingDate()).isEqualTo(currentDate);
        ArgumentCaptor<BreadthCalculator.Result> calculated = ArgumentCaptor.forClass(BreadthCalculator.Result.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BreadthService.InputLink>> links = ArgumentCaptor.forClass(List.class);
        verify(breadth).persist(any(), any(), any(), calculated.capture(), links.capture(), org.mockito.ArgumentMatchers.eq("EOD"));
        assertThat(calculated.getValue()).isEqualTo(
                new BreadthCalculator.Result(1, 1, 0, 1, 3,
                        List.of("MISSING_REFERENCE_PRICE", "REFERENCE_PRICE_UNAVAILABLE_USING_PRIOR_CLOSE")));
        assertThat(links.getValue()).extracting(BreadthService.InputLink::classification)
                .containsExactly("ADVANCING", "DECLINING", "UNCLASSIFIED");
        assertThat(links.getValue()).extracting(BreadthService.InputLink::reasonCode)
                .containsExactly(null, "REFERENCE_PRICE_UNAVAILABLE_USING_PRIOR_CLOSE", "MISSING_REFERENCE_PRICE");
        verify(regimes).reconcileEndOfDayIfMissingOrOlder(currentDate, persisted);
    }

    @Test
    void skipsWithoutFabricatingBreadthWhenTheActiveUniverseHasNoDailyBars() {
        UUID fpt = UUID.randomUUID();
        when(instruments.findByListedToIsNullAndInstrumentTypeAndStatusOrderByVenueAscSymbolAsc(
                "COMMON_EQUITY", "ACTIVE"))
                .thenReturn(List.of(instrument(fpt, "HOSE", "FPT")));
        when(stockReferenceData.findLatestDailyBars(List.of(fpt), 2)).thenReturn(List.of());
        var service = new HistoricalMarketBreadthReconciliationService(
                instruments, stockReferenceData, breadth, regimes);

        var result = service.reconcileLatestCompletedSession();

        assertThat(result.status()).isEqualTo("SKIPPED");
        assertThat(result.reasonCode()).isEqualTo("NO_DAILY_BAR_HISTORY");
        verify(breadth, never()).persist(any(), any(), any(), any(), any(), any());
        verify(regimes, never()).reconcileEndOfDayIfMissingOrOlder(any(), any());
    }

    private static MarketInstrumentEntity instrument(UUID id, String venue, String symbol) {
        return new MarketInstrumentEntity(id, null, venue, symbol, "COMMON_EQUITY",
                LocalDate.of(2020, 1, 1), null, "ACTIVE", "VNSTOCK_KBS", "revision");
    }

    private static StockReferenceDataService.DailyBarReference bar(
            UUID instrumentId, LocalDate tradingDate, String closePrice, String acceptedAt) {
        return bar(instrumentId, tradingDate, closePrice, null, acceptedAt);
    }

    private static StockReferenceDataService.DailyBarReference bar(
            UUID instrumentId, LocalDate tradingDate, String closePrice, String referencePrice, String acceptedAt) {
        return new StockReferenceDataService.DailyBarReference(UUID.randomUUID(), instrumentId, tradingDate,
                new BigDecimal(closePrice), new BigDecimal(closePrice), new BigDecimal(closePrice),
                new BigDecimal(closePrice), referencePrice == null ? null : new BigDecimal(referencePrice),
                1000L, BigDecimal.valueOf(1000000), "VNSTOCK_KBS",
                Instant.parse(acceptedAt));
    }
}
