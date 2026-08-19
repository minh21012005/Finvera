package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.entity.SectorReferenceEntity;
import com.minhnb.finvera_be.stock.entity.StrategySignalEntity;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.SectorReferenceRepository;
import com.minhnb.finvera_be.stock.repository.StrategySignalRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DefaultStockReferenceDataService implements StockReferenceDataService {

    private static final String DEFAULT_SIGNAL_RULE_VERSION = "strategy-signal-v1";

    private final StrategySignalRepository strategySignals;
    private final EquityDailyBarRepository dailyBars;
    private final EquityProfileRepository equityProfiles;
    private final SectorReferenceRepository sectorReferences;

    public DefaultStockReferenceDataService(
            StrategySignalRepository strategySignals,
            EquityDailyBarRepository dailyBars,
            EquityProfileRepository equityProfiles,
            SectorReferenceRepository sectorReferences) {
        this.strategySignals = strategySignals;
        this.dailyBars = dailyBars;
        this.equityProfiles = equityProfiles;
        this.sectorReferences = sectorReferences;
    }

    @Override
    public List<SignalReference> findCurrentSignalsForInstruments(Collection<UUID> instrumentIds) {
        Objects.requireNonNull(instrumentIds, "instrumentIds");
        if (instrumentIds.isEmpty()) {
            return List.of();
        }
        return strategySignals
                .findLatestCurrentByInstrumentIdInAndRuleVersion(instrumentIds, DEFAULT_SIGNAL_RULE_VERSION)
                .stream()
                .map(DefaultStockReferenceDataService::toSignalReference)
                .toList();
    }

    @Override
    public Optional<DailyBarReference> findLatestDailyBar(UUID instrumentId) {
        Objects.requireNonNull(instrumentId, "instrumentId");
        return dailyBars.findFirstByInstrumentIdAndCurrentTrueOrderByTradingDateDescAcceptedAtDesc(instrumentId)
                .map(DefaultStockReferenceDataService::toDailyBarReference);
    }

    @Override
    public List<DailyBarReference> findLatestDailyBars(Collection<UUID> instrumentIds) {
        Objects.requireNonNull(instrumentIds, "instrumentIds");
        if (instrumentIds.isEmpty()) {
            return List.of();
        }
        return dailyBars.findLatestNCurrentByInstrumentIdIn(instrumentIds, 1)
                .stream()
                .map(DefaultStockReferenceDataService::toDailyBarReference)
                .toList();
    }

    @Override
    public List<DailyBarReference> findDailyBars(UUID instrumentId, LocalDate fromInclusive, LocalDate toInclusive) {
        Objects.requireNonNull(instrumentId, "instrumentId");
        Objects.requireNonNull(fromInclusive, "fromInclusive");
        Objects.requireNonNull(toInclusive, "toInclusive");
        return dailyBars.findByInstrumentIdAndCurrentTrueAndTradingDateBetweenOrderByTradingDateAsc(
                        instrumentId, fromInclusive, toInclusive)
                .stream()
                .map(DefaultStockReferenceDataService::toDailyBarReference)
                .toList();
    }

    @Override
    public Optional<EquityProfileReference> findEquityProfile(UUID instrumentId) {
        Objects.requireNonNull(instrumentId, "instrumentId");
        return equityProfiles.findFirstByInstrumentIdAndEffectiveToIsNull(instrumentId)
                .map(this::toEquityProfileReference);
    }

    @Override
    public List<EquityProfileReference> findEquityProfiles(Collection<UUID> instrumentIds) {
        Objects.requireNonNull(instrumentIds, "instrumentIds");
        if (instrumentIds.isEmpty()) {
            return List.of();
        }
        List<EquityProfileEntity> profiles = equityProfiles.findByInstrumentIdInAndEffectiveToIsNull(instrumentIds);
        if (profiles.isEmpty()) {
            return List.of();
        }
        List<UUID> sectorRefIds = profiles.stream()
                .map(EquityProfileEntity::getSectorReferenceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, SectorReferenceEntity> sectorMap = sectorRefIds.isEmpty()
                ? Collections.emptyMap()
                : sectorReferences.findAllById(sectorRefIds).stream()
                        .collect(Collectors.toMap(SectorReferenceEntity::getId, Function.identity()));

        return profiles.stream()
                .map(p -> toEquityProfileReference(p, sectorMap.get(p.getSectorReferenceId())))
                .toList();
    }

    private EquityProfileReference toEquityProfileReference(EquityProfileEntity profile) {
        SectorReferenceEntity sector = null;
        if (profile.getSectorReferenceId() != null) {
            sector = sectorReferences.findById(profile.getSectorReferenceId()).orElse(null);
        }
        return toEquityProfileReference(profile, sector);
    }

    private static EquityProfileReference toEquityProfileReference(
            EquityProfileEntity profile, SectorReferenceEntity sector) {
        return new EquityProfileReference(
                profile.getInstrumentId(),
                profile.getCompanyNameVi(),
                profile.getCompanyNameEn(),
                profile.getSectorReferenceId(),
                sector != null ? sector.getDisplayNameVi() : null,
                sector != null ? sector.getDisplayNameEn() : null);
    }

    private static SignalReference toSignalReference(StrategySignalEntity entity) {
        return new SignalReference(
                entity.getId(),
                entity.getInstrumentId(),
                entity.getStrategyCode(),
                entity.getRuleVersion(),
                entity.getAsOfTradingDate(),
                entity.getDirection(),
                entity.getEntryLow(),
                entity.getEntryHigh(),
                entity.getStopLoss(),
                entity.getTarget1(),
                entity.getTarget2(),
                entity.getRiskReward(),
                entity.getRiskScore(),
                entity.getRiskLevel(),
                entity.getCalculatedAt());
    }

    private static DailyBarReference toDailyBarReference(EquityDailyBarEntity entity) {
        return new DailyBarReference(
                entity.getId(),
                entity.getInstrumentId(),
                entity.getTradingDate(),
                entity.getOpenPrice(),
                entity.getHighPrice(),
                entity.getLowPrice(),
                entity.getClosePrice(),
                entity.getVolume(),
                entity.getValueVnd(),
                entity.getSource(),
                entity.getAcceptedAt());
    }
}
