package com.minhnb.finvera_be.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.repository.MarketImportBatchRepository;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.market.service.MarketInstrumentReferenceImportService.InstrumentReferenceRecord;
import com.minhnb.finvera_be.market.service.MarketInstrumentReferenceImportService.PackageInput;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MarketInstrumentReferenceImportServiceTests {

    private final MarketImportBatchRepository batches = mock(MarketImportBatchRepository.class);
    private final MarketInstrumentRepository instruments = mock(MarketInstrumentRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-22T00:00:00Z"), ZoneOffset.UTC);
    private final MarketInstrumentReferenceImportService service =
            new MarketInstrumentReferenceImportService(batches, instruments, clock);

    @Test
    void rejectsAnUnsupportedContractVersion() {
        PackageInput input = packageWith("wrong-contract", List.of(record("HOSE", "VNM")));
        assertThatThrownBy(() -> service.importPackage(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UNSUPPORTED_CONTRACT");
    }

    @Test
    void registersOnlySymbolsThatDoNotAlreadyHaveAnActiveInstrument() {
        when(instruments.findFirstBySymbolAndListedToIsNull("VNM"))
                .thenReturn(Optional.of(mock(MarketInstrumentEntity.class)));
        when(instruments.findFirstBySymbolAndListedToIsNull("FPT")).thenReturn(Optional.empty());
        when(instruments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PackageInput input = packageWith(MarketInstrumentReferenceImportService.CONTRACT_VERSION,
                List.of(record("HOSE", "FPT"), record("HOSE", "VNM")));
        var result = service.importPackage(input);

        assertThat(result.status()).isEqualTo(MarketInstrumentReferenceImportService.Status.APPLIED);
        assertThat(result.registered()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        verify(instruments, times(1)).save(any());
    }

    @Test
    void replayingTheSamePackageIsIdempotent() {
        when(batches.existsByPackageSha256(any())).thenReturn(true);

        PackageInput input = packageWith(MarketInstrumentReferenceImportService.CONTRACT_VERSION,
                List.of(record("HOSE", "FPT")));
        var result = service.importPackage(input);

        assertThat(result.status()).isEqualTo(MarketInstrumentReferenceImportService.Status.ALREADY_APPLIED);
        verify(instruments, times(0)).save(any());
    }

    private static InstrumentReferenceRecord record(String venue, String symbol) {
        String canonical = "{\"instrumentStatus\":\"UNKNOWN\",\"isin\":null,\"listedFrom\":\"2026-08-22\",\"symbol\":\""
                + symbol + "\",\"venue\":\"" + venue + "\"}";
        return new InstrumentReferenceRecord(venue, symbol, null, LocalDate.of(2026, 8, 22), "UNKNOWN", canonical);
    }

    private PackageInput packageWith(String contractVersion, List<InstrumentReferenceRecord> records) {
        List<InstrumentReferenceRecord> sorted = records.stream()
                .sorted((a, b) -> a.venue().equals(b.venue()) ? a.symbol().compareTo(b.symbol())
                        : a.venue().compareTo(b.venue()))
                .toList();
        String payload = "{\"records\":[]}";
        return new PackageInput(contractVersion, "finvera-vnstock-exporter", "0.1.0", "VNSTOCK_KBS",
                MarketInstrumentReferenceImportService.sha256(payload), payload,
                Instant.parse("2026-08-22T00:00:00Z"), sorted);
    }
}
