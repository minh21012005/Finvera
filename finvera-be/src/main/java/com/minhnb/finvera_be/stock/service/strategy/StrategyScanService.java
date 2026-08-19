package com.minhnb.finvera_be.stock.service.strategy;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorCode;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorComponent;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.DailyBarPoint;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.IndicatorSnapshot;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.EntryEvaluation;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.EntryStatus;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.RiskAssessment;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.StrategyInputs;
import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorResultEntity;
import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorValueEntity;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorResultRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorValueRepository;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService.SignalDetail;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-009 to FR-011, NFR-002 (research R-007). Finds every {@code LISTED}
 * instrument currently triggering one strategy. Reuses Feature 003's
 * two-pass, bulk-fetch pattern; unlike {@link StrategySignalService}, a scan
 * never persists — R-007 defines the scan as always evaluating on demand, so
 * eight strategy conditions stay defined exactly once (the {@code
 * StrategySignalV1} engine) and behave identically whether reached from a
 * single-stock view or a universe scan.
 */
@Service
public class StrategyScanService {

    private static final int MAX_BARS_PER_INSTRUMENT = 21;
    private static final int CURRENT_AND_PRIOR = 2;
    private static final String LISTED = "LISTED";

    private final EquityProfileRepository equityProfiles;
    private final EquityDailyBarRepository dailyBars;
    private final TechnicalIndicatorResultRepository technicalResults;
    private final TechnicalIndicatorValueRepository technicalValues;
    private final MarketReferenceDataService referenceData;
    private final RiskFactorInputAssembler riskFactorInputs;

    public StrategyScanService(
            EquityProfileRepository equityProfiles,
            EquityDailyBarRepository dailyBars,
            TechnicalIndicatorResultRepository technicalResults,
            TechnicalIndicatorValueRepository technicalValues,
            MarketReferenceDataService referenceData,
            RiskFactorInputAssembler riskFactorInputs) {
        this.equityProfiles = equityProfiles;
        this.dailyBars = dailyBars;
        this.technicalResults = technicalResults;
        this.technicalValues = technicalValues;
        this.referenceData = referenceData;
        this.riskFactorInputs = riskFactorInputs;
    }

    @Transactional(readOnly = true)
    public ScanResult scan(StrategyCode strategyCode, int limit, int offset) {
        List<EquityProfileEntity> profiles = equityProfiles.findByEffectiveToIsNullAndListingStatus(LISTED);
        List<UUID> allInstrumentIds = profiles.stream().map(EquityProfileEntity::getInstrumentId).toList();
        Map<UUID, InstrumentReference> instrumentsById = referenceData.findInstrumentsByIds(allInstrumentIds)
                .stream().collect(Collectors.toMap(InstrumentReference::instrumentId, r -> r));

        List<EquityDailyBarEntity> bars = dailyBars
                .findLatestNCurrentByInstrumentIdIn(allInstrumentIds, MAX_BARS_PER_INSTRUMENT);
        Map<UUID, List<EquityDailyBarEntity>> barsByInstrument = bars.stream()
                .collect(Collectors.groupingBy(EquityDailyBarEntity::getInstrumentId, LinkedHashMap::new,
                        Collectors.toList()));

        List<TechnicalIndicatorResultEntity> resultRows = technicalResults
                .findLatestNCurrentByInstrumentIdInAndRuleVersion(allInstrumentIds,
                        com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.RULE_VERSION,
                        CURRENT_AND_PRIOR);
        List<UUID> allResultIds = resultRows.stream().map(TechnicalIndicatorResultEntity::getId).toList();
        Map<UUID, List<TechnicalIndicatorValueEntity>> valuesByResult = technicalValues.findByResultIdIn(allResultIds)
                .stream().collect(Collectors.groupingBy(TechnicalIndicatorValueEntity::getResultId));
        Map<UUID, List<TechnicalIndicatorResultEntity>> resultsByInstrument = resultRows.stream()
                .collect(Collectors.groupingBy(TechnicalIndicatorResultEntity::getInstrumentId));

        List<ScanMatch> matches = new ArrayList<>();
        int excludedForInsufficientHistory = 0;

        for (EquityProfileEntity profile : profiles) {
            UUID instrumentId = profile.getInstrumentId();
            List<EquityDailyBarEntity> orderedBars = barsByInstrument.getOrDefault(instrumentId, List.of());
            if (orderedBars.isEmpty()) {
                excludedForInsufficientHistory++;
                continue;
            }
            EquityDailyBarEntity latestBar = orderedBars.get(orderedBars.size() - 1);
            BigDecimal close = latestBar.getClosePrice();
            List<DailyBarPoint> recentBarPoints = orderedBars.stream()
                    .map(b -> new DailyBarPoint(b.getTradingDate(), b.getClosePrice(), b.getHighPrice(), b.getLowPrice()))
                    .toList();

            Map<IndicatorCode, IndicatorSnapshot> current = new EnumMap<>(IndicatorCode.class);
            Map<IndicatorCode, IndicatorSnapshot> prior = new EnumMap<>(IndicatorCode.class);
            for (var entry : resultsByInstrument.getOrDefault(instrumentId, List.of()).stream()
                    .collect(Collectors.groupingBy(r -> parseIndicatorCode(r.getIndicatorCode()))).entrySet()) {
                IndicatorCode code = entry.getKey();
                if (code == null) {
                    continue;
                }
                List<TechnicalIndicatorResultEntity> rows = entry.getValue().stream()
                        .sorted(Comparator.comparing(TechnicalIndicatorResultEntity::getAsOfTradingDate).reversed())
                        .toList();
                var currentRow = rows.get(0);
                current.put(code, toSnapshot(currentRow, valuesByResult.getOrDefault(currentRow.getId(), List.of())));
                if (rows.size() > 1) {
                    var priorRow = rows.get(1);
                    prior.put(code, toSnapshot(priorRow, valuesByResult.getOrDefault(priorRow.getId(), List.of())));
                }
            }

            StrategyInputs inputs = new StrategyInputs(close, current, prior, recentBarPoints);
            EntryEvaluation eval = StrategySignalV1.evaluate(strategyCode, inputs);
            if (eval.status() == EntryStatus.INSUFFICIENT_HISTORY) {
                excludedForInsufficientHistory++;
                continue;
            }
            if (eval.status() != EntryStatus.SIGNAL) {
                continue;
            }

            var riskContext = riskFactorInputs.build(instrumentId, current.get(IndicatorCode.ATR14),
                    current.get(IndicatorCode.RELATIVE_VOLUME));
            RiskAssessment risk = StrategySignalV1.computeRisk(close, eval.levels(), riskContext.inputs());
            InstrumentReference instrument = instrumentsById.get(instrumentId);
            SignalDetail signal = new SignalDetail(strategyCode, StrategySignalV1.RULE_VERSION, eval.direction(),
                    eval.levels(), risk.overallScore(), risk.riskLevel(), risk.signalStrength(), risk.factors(),
                    eval.supportingEvidence(), risk.reasonCodes(), latestBar.getTradingDate(), Instant.now());
            matches.add(new ScanMatch(
                    instrument != null ? instrument.symbol() : null,
                    profile.getCompanyNameVi(),
                    instrument != null ? instrument.venue() : null,
                    signal));
        }

        matches.sort(Comparator.comparing(ScanMatch::symbol, Comparator.nullsLast(Comparator.naturalOrder())));
        int totalMatchCount = matches.size();
        int fromIndex = Math.min(offset, totalMatchCount);
        int toIndex = Math.min(fromIndex + limit, totalMatchCount);
        List<ScanMatch> page = matches.subList(fromIndex, toIndex);

        return new ScanResult(strategyCode, List.copyOf(page), totalMatchCount, limit, offset,
                excludedForInsufficientHistory, Instant.now());
    }

    private static IndicatorCode parseIndicatorCode(String code) {
        try {
            return IndicatorCode.valueOf(code);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static IndicatorSnapshot toSnapshot(TechnicalIndicatorResultEntity result,
            List<TechnicalIndicatorValueEntity> values) {
        Map<IndicatorComponent, BigDecimal> components = new EnumMap<>(IndicatorComponent.class);
        for (TechnicalIndicatorValueEntity value : values) {
            try {
                components.put(IndicatorComponent.valueOf(value.getComponentCode()), value.getValue());
            } catch (IllegalArgumentException ignored) {
                // Forward-compatible with a component this rule version does not know yet.
            }
        }
        MetricApplicability applicability = "CURRENT".equals(result.getDataStatus()) || result.getQualityReason() == null
                ? MetricApplicability.DEFINED : MetricApplicability.MISSING;
        return new IndicatorSnapshot(applicability, components, result.getQualityReason());
    }

    public record ScanMatch(String symbol, String companyName, String exchange, SignalDetail signal) {
    }

    public record ScanResult(StrategyCode strategyCode, List<ScanMatch> matches, int totalMatchCount, int limit,
            int offset, int excludedForInsufficientHistoryCount, Instant calculatedAt) {
    }
}
