package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.service.MarketImportBatchService;
import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.service.StockHistoryImportService.DailyBarRecord;
import com.minhnb.finvera_be.stock.service.StockHistoryImportService.PackageInput;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingDailyBar;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionResult;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockHistoryImportServiceTests {

    private final StockIngestionService ingestion = mock(StockIngestionService.class);
    private final MarketImportBatchService importBatches = mock(MarketImportBatchService.class);
    private final EquityDailyBarRepository dailyBars = mock(EquityDailyBarRepository.class);
    private final StockHistoryImportService service = new StockHistoryImportService(ingestion, importBatches, dailyBars);

    @Test
    void rejectsAnUnsupportedContractVersion() {
        PackageInput input = packageWith("wrong-contract", List.of(record("2026-01-15")));
        assertThatThrownBy(() -> service.importPackage(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UNSUPPORTED_CONTRACT");
    }

    @Test
    void rejectsAMismatchedChecksum() {
        DailyBarRecord r = record("2026-01-15");
        String payload = canonicalPayload(r);
        PackageInput input = new PackageInput(
                "vnstock-daily-bar-v1", "finvera-vnstock-exporter", "0.1.0", "VNSTOCK_KBS", "VNM",
                "0000000000000000000000000000000000000000000000000000000000000000", payload,
                Instant.parse("2026-01-16T00:00:00Z"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                List.of(r));
        assertThatThrownBy(() -> service.importPackage(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_CHECKSUM");
    }

    @Test
    void delegatesEachRecordToIngestDailyBarWithTheDeclaredSource() {
        UUID barId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        UUID importBatchId = UUID.randomUUID();
        when(importBatches.recordAcceptedBatch(any())).thenReturn(importBatchId);
        when(ingestion.ingestDailyBar(any())).thenReturn(
                new IngestionResult(IngestionStatus.ACCEPTED, null, barId, 1));
        when(dailyBars.findById(barId)).thenReturn(Optional.of(new EquityDailyBarEntity(
                barId, instrumentId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 1, 15),
                null, null, null, null, null, null, "RAW", null, null, "VNSTOCK_KBS",
                Instant.parse("2026-01-15T08:00:00Z"), Instant.parse("2026-01-16T00:00:00Z"),
                1, true, null, null)));
        when(dailyBars.deleteUnreferencedSupersededDailyBars(instrumentId, "VNSTOCK_KBS")).thenReturn(1);
        DailyBarRecord r = record("2026-01-15");
        PackageInput input = packageWith("vnstock-daily-bar-v1", List.of(r));

        var summary = service.importPackage(input);

        assertThat(summary.results()).hasSize(1);
        assertThat(summary.results().get(0).status()).isEqualTo(IngestionStatus.ACCEPTED);
        assertThat(summary.prunedRows()).isEqualTo(1);
        verify(ingestion, times(1)).ingestDailyBar(org.mockito.ArgumentMatchers.argThat(
                (IncomingDailyBar incoming) -> incoming.source().equals("VNSTOCK_KBS")
                        && incoming.symbol().equals("VNM")
                        && incoming.tradingDate().equals(LocalDate.of(2026, 1, 15))
                        && incoming.adjustmentStatus().equals("RAW")
                        && incoming.importBatchId() != null));
        verify(importBatches).recordAcceptedBatch(org.mockito.ArgumentMatchers.argThat(
                (MarketImportBatchService.AcceptedBatch batch) ->
                        batch.packageSha256().equals(input.packageSha256())
                        && batch.recordCount() == 1
                        && batch.upstreamSource().equals("VNSTOCK_KBS")));
        verify(dailyBars).clearSupersededDailyBarLinks(instrumentId, "VNSTOCK_KBS");
        verify(dailyBars).deleteUnreferencedSupersededDailyBars(instrumentId, "VNSTOCK_KBS");
    }

    private static DailyBarRecord record(String tradingDate) {
        return new DailyBarRecord("VNM", LocalDate.parse(tradingDate), Instant.parse(tradingDate + "T08:00:00Z"),
                "10.000000", "10.500000", "9.800000", "10.200000", "1000000", "10200000000000", "RAW", "");
    }

    private PackageInput packageWith(String contractVersion, List<DailyBarRecord> records) {
        List<DailyBarRecord> canonicalized = records.stream()
                .map(r -> new DailyBarRecord(r.symbol(), r.tradingDate(), r.observedAt(), r.open(), r.high(),
                        r.low(), r.close(), r.volume(), r.valueVnd(), r.adjustmentStatus(), canonicalPayload(r)))
                .toList();
        String payload = "{\"records\":[]}"; // checksum correctness is exercised separately above
        return new PackageInput(contractVersion, "finvera-vnstock-exporter", "0.1.0", "VNSTOCK_KBS", "VNM",
                StockHistoryImportService.sha256(payload), payload, Instant.parse("2026-01-16T00:00:00Z"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), canonicalized);
    }

    private static String canonicalPayload(DailyBarRecord r) {
        return "{\"symbol\":\"" + r.symbol() + "\",\"tradingDate\":\"" + r.tradingDate() + "\"}";
    }
}
