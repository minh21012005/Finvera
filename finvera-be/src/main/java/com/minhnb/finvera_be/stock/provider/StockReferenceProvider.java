package com.minhnb.finvera_be.stock.provider;

import java.util.List;

/** Read-only instrument identity and sector classification port (contracts/stock-data-provider.md). */
public interface StockReferenceProvider {

    InstrumentReference findInstrument(String symbol);

    List<SectorClassification> listSectorClassification();

    record InstrumentReference(
            String symbol,
            String venue,
            String companyNameVi,
            String companyNameEn,
            String listingStatus,
            Long sharesOutstanding,
            String sectorCode,
            String sectorScheme,
            String sectorSchemeVersion,
            String source,
            String sourceRevision) {
    }

    record SectorClassification(
            String scheme,
            String schemeVersion,
            String sectorCode,
            String displayNameVi,
            String displayNameEn) {
    }

    final class InstrumentNotFoundException extends RuntimeException {
        public InstrumentNotFoundException(String symbol) {
            super("No accepted reference data for symbol: " + symbol);
        }
    }
}
