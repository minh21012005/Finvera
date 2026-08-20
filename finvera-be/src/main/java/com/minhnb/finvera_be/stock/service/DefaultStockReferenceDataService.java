package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorCode;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorComponent;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.IndicatorSnapshot;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1;
import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.entity.SectorReferenceEntity;
import com.minhnb.finvera_be.stock.entity.StrategySignalEntity;
import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorResultEntity;
import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorValueEntity;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.SectorReferenceRepository;
import com.minhnb.finvera_be.stock.repository.StrategySignalRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorResultRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorValueRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
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
    private final TechnicalIndicatorResultRepository technicalResults;
    private final TechnicalIndicatorValueRepository technicalValues;

    public DefaultStockReferenceDataService(
            StrategySignalRepository strategySignals,
            EquityDailyBarRepository dailyBars,
            EquityProfileRepository equityProfiles,
            SectorReferenceRepository sectorReferences,
            TechnicalIndicatorResultRepository technicalResults,
            TechnicalIndicatorValueRepository technicalValues) {
        this.strategySignals = strategySignals;
        this.dailyBars = dailyBars;
        this.equityProfiles = equityProfiles;
        this.sectorReferences = sectorReferences;
        this.technicalResults = technicalResults;
        this.technicalValues = technicalValues;
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

    @Override
    public Map<UUID, Map<IndicatorCode, IndicatorSnapshot>> findLatestTechnicalIndicators(
            Collection<UUID> instrumentIds) {
        Objects.requireNonNull(instrumentIds, "instrumentIds");
        if (instrumentIds.isEmpty()) {
            return Map.of();
        }

        List<TechnicalIndicatorResultEntity> results = technicalResults
                .findLatestCurrentByInstrumentIdInAndRuleVersion(instrumentIds, TechnicalIndicatorsV1.RULE_VERSION);
        List<UUID> resultIds = results.stream().map(TechnicalIndicatorResultEntity::getId).toList();
        Map<UUID, List<TechnicalIndicatorValueEntity>> valuesByResultId = technicalValues.findByResultIdIn(resultIds)
                .stream().collect(Collectors.groupingBy(TechnicalIndicatorValueEntity::getResultId));

        Map<UUID, Map<IndicatorCode, IndicatorSnapshot>> byInstrument = new HashMap<>();
        for (TechnicalIndicatorResultEntity result : results) {
            IndicatorCode code;
            try {
                code = IndicatorCode.valueOf(result.getIndicatorCode());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            Map<IndicatorComponent, BigDecimal> components = new EnumMap<>(IndicatorComponent.class);
            for (TechnicalIndicatorValueEntity value : valuesByResultId.getOrDefault(result.getId(), List.of())) {
                try {
                    components.put(IndicatorComponent.valueOf(value.getComponentCode()), value.getValue());
                } catch (IllegalArgumentException ignored) {
                    // Forward-compatible with a component this rule version does not know yet.
                }
            }
            IndicatorSnapshot snapshot = new IndicatorSnapshot(
                    MetricApplicability.valueOf(
                            result.getDataStatus().equals("CURRENT") || result.getQualityReason() == null
                                    ? "DEFINED" : "MISSING"),
                    components, result.getQualityReason());
            byInstrument.computeIfAbsent(result.getInstrumentId(), k -> new EnumMap<>(IndicatorCode.class))
                    .put(code, snapshot);
        }
        return byInstrument;
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
