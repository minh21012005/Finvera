package com.minhnb.finvera_be.stock.service.strategy;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.RegimeAssessmentReference;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorComponent;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.IndicatorSnapshot;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.RiskFactorInputs;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.RiskFactorInputs.MetricPoint;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1;
import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorResultEntity;
import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorValueEntity;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorResultRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorValueRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Shared by {@link StrategySignalService} (single-stock view) and {@link
 * StrategyScanService} (universe scan) — both need the same six-factor raw
 * input assembly for a triggering candidate, only ever run for a strategy
 * that has already triggered (never for the full candidate universe), per
 * plan.md's ownership note that the trailing-250-session queries this class
 * issues are too costly to run against every scanned candidate.
 */
@Component
class RiskFactorInputAssembler {

    private static final int TRAILING_RISK_WINDOW = 250;
    private static final String INPUT_UNAVAILABLE = "INPUT_UNAVAILABLE";

    private final MarketReferenceDataService referenceData;
    private final EquityDailyBarRepository dailyBars;
    private final TechnicalIndicatorResultRepository technicalResults;
    private final TechnicalIndicatorValueRepository technicalValues;

    RiskFactorInputAssembler(
            MarketReferenceDataService referenceData,
            EquityDailyBarRepository dailyBars,
            TechnicalIndicatorResultRepository technicalResults,
            TechnicalIndicatorValueRepository technicalValues) {
        this.referenceData = referenceData;
        this.dailyBars = dailyBars;
        this.technicalResults = technicalResults;
        this.technicalValues = technicalValues;
    }

    RiskContext build(UUID instrumentId, IndicatorSnapshot currentAtr14, IndicatorSnapshot currentRelativeVolume) {
        MetricPoint volatility = metricPointFromComponent(currentAtr14, IndicatorComponent.PERCENT_OF_CLOSE);
        MetricPoint atrValue = metricPointFromComponent(currentAtr14, IndicatorComponent.VALUE);

        List<TechnicalIndicatorResultEntity> atrHistory = technicalResults
                .findLatestNCurrentByInstrumentIdInAndRuleVersionAndIndicatorCode(List.of(instrumentId),
                        TechnicalIndicatorsV1.RULE_VERSION, "ATR14", TRAILING_RISK_WINDOW);
        MetricPoint trailingAtr = averageValueComponent(atrHistory);

        List<EquityDailyBarEntity> barHistory = dailyBars
                .findLatestNCurrentByInstrumentIdIn(List.of(instrumentId), TRAILING_RISK_WINDOW);
        MetricPoint highestClose = barHistory.isEmpty() ? MetricPoint.unavailable(INPUT_UNAVAILABLE)
                : MetricPoint.of(barHistory.stream().map(EquityDailyBarEntity::getClosePrice)
                        .max(BigDecimal::compareTo).orElseThrow());

        MetricPoint liquidity = metricPointFromComponent(currentRelativeVolume, IndicatorComponent.VALUE);

        Optional<RegimeAssessmentReference> regimeOpt = referenceData.findCurrentRegimeAssessment();
        MetricPoint regime;
        UUID regimeAssessmentId = null;
        if (regimeOpt.isPresent() && regimeOpt.get().score() != null && isUsableRegime(regimeOpt.get().dataStatus())) {
            regime = MetricPoint.of(BigDecimal.valueOf(regimeOpt.get().score()));
            regimeAssessmentId = regimeOpt.get().id();
        } else {
            regime = MetricPoint.unavailable(regimeOpt.map(r -> r.dataStatus().name()).orElse("REGIME_UNAVAILABLE"));
        }

        return new RiskContext(
                new RiskFactorInputs(volatility, atrValue, trailingAtr, highestClose, liquidity, regime),
                regimeAssessmentId);
    }

    private static boolean isUsableRegime(DataStatus status) {
        return status == DataStatus.CURRENT || status == DataStatus.DELAYED;
    }

    private MetricPoint averageValueComponent(List<TechnicalIndicatorResultEntity> rows) {
        if (rows.isEmpty()) {
            return MetricPoint.unavailable(INPUT_UNAVAILABLE);
        }
        List<UUID> resultIds = rows.stream().map(TechnicalIndicatorResultEntity::getId).toList();
        List<BigDecimal> values = technicalValues.findByResultIdIn(resultIds).stream()
                .filter(v -> IndicatorComponent.VALUE.name().equals(v.getComponentCode()) && v.getValue() != null)
                .map(TechnicalIndicatorValueEntity::getValue)
                .toList();
        if (values.isEmpty()) {
            return MetricPoint.unavailable(INPUT_UNAVAILABLE);
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = com.minhnb.finvera_be.stock.domain.model.DecimalMath.divide12(sum,
                BigDecimal.valueOf(values.size()));
        return MetricPoint.of(average);
    }

    private static MetricPoint metricPointFromComponent(IndicatorSnapshot snapshot, IndicatorComponent component) {
        if (snapshot == null || snapshot.applicability() != MetricApplicability.DEFINED) {
            return MetricPoint.unavailable(
                    snapshot == null || snapshot.qualityReason() == null ? INPUT_UNAVAILABLE
                            : snapshot.qualityReason());
        }
        BigDecimal value = snapshot.components().get(component);
        return value == null ? MetricPoint.unavailable(INPUT_UNAVAILABLE) : MetricPoint.of(value);
    }

    record RiskContext(RiskFactorInputs inputs, UUID regimeAssessmentId) {
    }
}
