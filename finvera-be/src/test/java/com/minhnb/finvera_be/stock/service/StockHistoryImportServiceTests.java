package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.stock.service.StockHistoryImportService.DailyBarRecord;
import com.minhnb.finvera_be.stock.service.StockHistoryImportService.PackageInput;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingDailyBar;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionResult;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StockHistoryImportServiceTests {

    private final StockIngestionService ingestion = mock(StockIngestionService.class);
    private final StockHistoryImportService service = new StockHistoryImportService(ingestion);

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
        when(ingestion.ingestDailyBar(any())).thenReturn(
                new IngestionResult(IngestionStatus.ACCEPTED, null, java.util.UUID.randomUUID(), 1));
        DailyBarRecord r = record("2026-01-15");
        PackageInput input = packageWith("vnstock-daily-bar-v1", List.of(r));

        var summary = service.importPackage(input);

        assertThat(summary.results()).hasSize(1);
        assertThat(summary.results().get(0).status()).isEqualTo(IngestionStatus.ACCEPTED);
        verify(ingestion, times(1)).ingestDailyBar(org.mockito.ArgumentMatchers.argThat(
                (IncomingDailyBar incoming) -> incoming.source().equals("VNSTOCK_KBS")
                        && incoming.symbol().equals("VNM")
                        && incoming.tradingDate().equals(LocalDate.of(2026, 1, 15))
                        && incoming.adjustmentStatus().equals("RAW")));
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
