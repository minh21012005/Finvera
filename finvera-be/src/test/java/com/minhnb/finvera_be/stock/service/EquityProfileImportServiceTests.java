package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.service.EquityProfileImportService.PackageInput;
import com.minhnb.finvera_be.stock.service.EquityProfileImportService.ProfileRecord;
import com.minhnb.finvera_be.stock.service.EquityProfileImportService.ProfileStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EquityProfileImportServiceTests {

    private final MarketReferenceDataService referenceData = mock(MarketReferenceDataService.class);
    private final EquityProfileRepository profiles = mock(EquityProfileRepository.class);
    private final EquityProfileImportService service = new EquityProfileImportService(referenceData, profiles);

    @Test
    void rejectsAnUnsupportedContractVersion() {
        PackageInput input = packageWith("wrong-contract", List.of(record("VNM")));
        assertThatThrownBy(() -> service.importPackage(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UNSUPPORTED_CONTRACT");
    }

    @Test
    void createsAProfileForAnInstrumentThatDoesNotHaveOneYet() {
        UUID instrumentId = UUID.randomUUID();
        when(referenceData.findActiveInstrumentBySymbol("VNM")).thenReturn(
                Optional.of(new InstrumentReference(instrumentId, "HOSE", "VNM", "COMMON_EQUITY", "ACTIVE")));
        when(profiles.findFirstByInstrumentIdAndEffectiveToIsNull(instrumentId)).thenReturn(Optional.empty());

        PackageInput input = packageWith(EquityProfileImportService.CONTRACT_VERSION, List.of(record("VNM")));
        var summary = service.importPackage(input);

        assertThat(summary.results()).hasSize(1);
        assertThat(summary.results().get(0).status()).isEqualTo(ProfileStatus.CREATED);
    }

    @Test
    void skipsWithoutDuplicatingWhenACurrentProfileAlreadyExists() {
        UUID instrumentId = UUID.randomUUID();
        when(referenceData.findActiveInstrumentBySymbol("VNM")).thenReturn(
                Optional.of(new InstrumentReference(instrumentId, "HOSE", "VNM", "COMMON_EQUITY", "ACTIVE")));
        when(profiles.findFirstByInstrumentIdAndEffectiveToIsNull(instrumentId))
                .thenReturn(Optional.of(mock(EquityProfileEntity.class)));

        PackageInput input = packageWith(EquityProfileImportService.CONTRACT_VERSION, List.of(record("VNM")));
        var summary = service.importPackage(input);

        assertThat(summary.results().get(0).status()).isEqualTo(ProfileStatus.ALREADY_PRESENT);
    }

    @Test
    void reportsUnknownInstrumentWithoutFabricatingAProfile() {
        when(referenceData.findActiveInstrumentBySymbol("ZZZZ")).thenReturn(Optional.empty());

        PackageInput input = packageWith(EquityProfileImportService.CONTRACT_VERSION, List.of(record("ZZZZ")));
        var summary = service.importPackage(input);

        assertThat(summary.results().get(0).status()).isEqualTo(ProfileStatus.UNKNOWN_INSTRUMENT);
    }

    private static ProfileRecord record(String symbol) {
        return new ProfileRecord(symbol, "CTCP " + symbol, symbol + " Corp", "UNKNOWN",
                LocalDate.of(2026, 8, 22), "SHARES_OUTSTANDING_UNAVAILABLE", "x");
    }

    private PackageInput packageWith(String contractVersion, List<ProfileRecord> records) {
        String payload = "{\"records\":[]}";
        return new PackageInput(contractVersion, "finvera-vnstock-exporter", "0.1.0", "VNSTOCK_KBS",
                EquityProfileImportService.sha256(payload), payload, Instant.parse("2026-08-22T00:00:00Z"), records);
    }
}
