package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.entity.SectorReferenceEntity;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.SectorReferenceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-016: symbol/name lookup restricted to supported instruments; never fabricates a placeholder entry. */
@Service
public class StockSearchService {

    private final MarketReferenceDataService referenceData;
    private final EquityProfileRepository profiles;
    private final SectorReferenceRepository sectors;

    public StockSearchService(
            MarketReferenceDataService referenceData,
            EquityProfileRepository profiles,
            SectorReferenceRepository sectors) {
        this.referenceData = referenceData;
        this.profiles = profiles;
        this.sectors = sectors;
    }

    @Transactional(readOnly = true)
    public List<Result> search(String query, int limit) {
        return referenceData.searchActiveInstrumentsBySymbolPrefix(query, limit).stream()
                .map(this::enrich)
                .toList();
    }

    private Result enrich(InstrumentReference reference) {
        var profile = profiles.findFirstByInstrumentIdAndEffectiveToIsNull(reference.instrumentId());
        String companyName = profile.map(EquityProfileEntity::getCompanyNameVi).orElse(reference.symbol());
        String sector = profile.map(EquityProfileEntity::getSectorReferenceId)
                .flatMap(sectors::findById)
                .map(SectorReferenceEntity::getDisplayNameVi)
                .orElse(null);
        String listingStatus = profile.map(EquityProfileEntity::getListingStatus).orElse(reference.status());
        return new Result(reference.symbol(), companyName, reference.venue(), sector, listingStatus);
    }

    public record Result(String symbol, String companyName, String exchange, String sector, String listingStatus) {
    }
}
