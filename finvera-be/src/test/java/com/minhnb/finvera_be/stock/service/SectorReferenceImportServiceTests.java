package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.entity.SectorReferenceEntity;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.SectorReferenceRepository;
import com.minhnb.finvera_be.stock.service.SectorReferenceImportService.ClassificationRecord;
import com.minhnb.finvera_be.stock.service.SectorReferenceImportService.ClassificationStatus;
import com.minhnb.finvera_be.stock.service.SectorReferenceImportService.PackageInput;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SectorReferenceImportServiceTests {

    private final MarketReferenceDataService referenceData = mock(MarketReferenceDataService.class);
    private final SectorReferenceRepository sectors = mock(SectorReferenceRepository.class);
    private final EquityProfileRepository profiles = mock(EquityProfileRepository.class);
    private final SectorReferenceImportService service =
            new SectorReferenceImportService(referenceData, sectors, profiles);

    @Test
    void rejectsAnUnsupportedContractVersion() {
        PackageInput input = packageWith("wrong-contract", List.of(record("VNM", "3")));
        assertThatThrownBy(() -> service.importPackage(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UNSUPPORTED_CONTRACT");
    }

    @Test
    void classifiesAKnownInstrumentAndReusesTheSameSectorRowForASharedCode() {
        UUID instrumentId = UUID.randomUUID();
        UUID sectorId = UUID.randomUUID();
        when(referenceData.findActiveInstrumentBySymbol("VNM")).thenReturn(
                Optional.of(new InstrumentReference(instrumentId, "HOSE", "VNM", "COMMON_EQUITY", "ACTIVE")));
        when(sectors.findBySchemeAndSchemeVersionAndSectorCode("KBS_INDUSTRY", "4.0.6", "3"))
                .thenReturn(Optional.of(new SectorReferenceEntity(sectorId, "KBS_INDUSTRY", "4.0.6", "3", "BDS", null)));
        when(profiles.updateSectorReferenceId(instrumentId, sectorId)).thenReturn(1);

        PackageInput input = packageWith("vnstock-sector-reference-v1", List.of(record("VNM", "3")));
        var summary = service.importPackage(input);

        assertThat(summary.results()).hasSize(1);
        assertThat(summary.results().get(0).status()).isEqualTo(ClassificationStatus.CLASSIFIED);
    }

    @Test
    void reportsUnknownInstrumentWithoutFabricatingAClassification() {
        when(referenceData.findActiveInstrumentBySymbol("ZZZZ")).thenReturn(Optional.empty());
        when(sectors.findBySchemeAndSchemeVersionAndSectorCode(any(), any(), any())).thenReturn(Optional.empty());
        when(sectors.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PackageInput input = packageWith("vnstock-sector-reference-v1", List.of(record("ZZZZ", "3")));
        var summary = service.importPackage(input);

        assertThat(summary.results().get(0).status()).isEqualTo(ClassificationStatus.UNKNOWN_INSTRUMENT);
    }

    private static ClassificationRecord record(String symbol, String sectorCode) {
        return new ClassificationRecord(symbol, sectorCode, "Bat dong san", null, "x");
    }

    private PackageInput packageWith(String contractVersion, List<ClassificationRecord> records) {
        String payload = "{\"records\":[]}";
        return new PackageInput(contractVersion, "finvera-vnstock-exporter", "0.1.0", "VNSTOCK_KBS",
                "KBS_INDUSTRY", "4.0.6", SectorReferenceImportService.sha256(payload), payload,
                Instant.parse("2026-01-16T00:00:00Z"), records);
    }
}
