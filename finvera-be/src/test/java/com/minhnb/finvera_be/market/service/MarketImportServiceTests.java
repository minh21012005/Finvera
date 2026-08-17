package com.minhnb.finvera_be.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.entity.MarketImportBatchEntity;
import com.minhnb.finvera_be.market.entity.MarketInstrumentEntity;
import com.minhnb.finvera_be.market.repository.EquityPriceObservationRepository;
import com.minhnb.finvera_be.market.repository.MarketImportBatchRepository;
import com.minhnb.finvera_be.market.repository.MarketInstrumentRepository;
import com.minhnb.finvera_be.market.repository.MarketObservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketImportServiceTests {
    private static final Instant GENERATED_AT = Instant.parse("2026-08-17T03:00:00Z");
    @Mock private MarketImportBatchRepository batches;
    @Mock private MarketInstrumentRepository instruments;
    @Mock private MarketObservationRepository observations;
    @Mock private EquityPriceObservationRepository prices;
    private MarketImportService service;

    @BeforeEach
    void setUp() {
        service = new MarketImportService(batches, instruments, observations, prices,
                Clock.fixed(Instant.parse("2026-08-17T03:05:00Z"), ZoneOffset.UTC));
    }

    @Test
    void verifiedPackagePersistsTraceableFactsAndAcceptedBatch() {
        var input = validPackage("canonical-package-v1");
        when(instruments.findByVenueAndSymbolAndListedFrom("HOSE", "FPT", LocalDate.of(2006, 12, 13)))
                .thenReturn(Optional.of(new MarketInstrumentEntity(UUID.randomUUID(), null, "HOSE", "FPT",
                        "COMMON_EQUITY", LocalDate.of(2006, 12, 13), null, "ACTIVE", "VNSTOCK_KBS", "a".repeat(64))));

        var result = service.importPackage(input);

        assertThat(result.status()).isEqualTo(MarketImportService.Status.APPLIED);
        var batch = ArgumentCaptor.forClass(MarketImportBatchEntity.class);
        verify(batches).save(batch.capture());
        assertThat(batch.getValue().getStatus()).isEqualTo("ACCEPTED");
        assertThat(batch.getValue().getRecordCount()).isEqualTo(1);
        verify(observations).save(any());
        verify(prices).save(any());
    }

    @Test
    void checksumMismatchRejectsBeforeAnyWrite() {
        var valid = validPackage("canonical-package-v1");
        var tampered = new MarketImportService.PackageInput(valid.contractVersion(), valid.toolName(), valid.toolVersion(),
                valid.upstreamSource(), valid.packageSha256(), "tampered", valid.generatedAt(), valid.rangeStart(),
                valid.rangeEnd(), valid.records());

        assertThatThrownBy(() -> service.importPackage(tampered)).hasMessage("INVALID_CHECKSUM");

        verify(batches, never()).save(any());
        verify(observations, never()).save(any());
        verify(prices, never()).save(any());
    }

    @Test
    void duplicatePackageDoesNotAppendFacts() {
        var input = validPackage("canonical-package-v1");
        when(batches.existsByPackageSha256(input.packageSha256())).thenReturn(true);

        var result = service.importPackage(input);

        assertThat(result.status()).isEqualTo(MarketImportService.Status.ALREADY_APPLIED);
        verify(instruments, never()).save(any());
        verify(observations, never()).save(any());
        verify(prices, never()).save(any());
    }

    @Test
    void nonDecimalAndNonMonotonicInputAreRejectedBeforeAnyWrite() {
        var valid = validPackage("canonical-package-v1");
        var invalidRecord = new MarketImportService.EquityHistoryRecord("HOSE", "FPT", null,
                LocalDate.of(2006, 12, 13), "ACTIVE", LocalDate.of(2026, 8, 15), GENERATED_AT,
                "1e3", "RAW", null, "record-2");
        var invalid = new MarketImportService.PackageInput(valid.contractVersion(), valid.toolName(), valid.toolVersion(),
                valid.upstreamSource(), valid.packageSha256(), valid.canonicalPayload(), valid.generatedAt(),
                valid.rangeStart(), valid.rangeEnd(), List.of(invalidRecord));

        assertThatThrownBy(() -> service.importPackage(invalid)).hasMessage("INVALID_DECIMAL");
        verify(batches, never()).save(any());
        verify(observations, never()).save(any());
    }

    private static MarketImportService.PackageInput validPackage(String payload) {
        return new MarketImportService.PackageInput(MarketImportService.CONTRACT_VERSION, "finvera-vnstock-exporter",
                "0.1.0", "VNSTOCK_KBS", MarketImportService.sha256(payload), payload, GENERATED_AT,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 15),
                List.of(new MarketImportService.EquityHistoryRecord("HOSE", "FPT", null,
                        LocalDate.of(2006, 12, 13), "ACTIVE", LocalDate.of(2026, 8, 15), GENERATED_AT,
                        "101.500000", "RAW", null, "record-1")));
    }
}
