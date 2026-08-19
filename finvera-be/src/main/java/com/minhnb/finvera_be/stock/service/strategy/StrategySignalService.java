package com.minhnb.finvera_be.stock.service.strategy;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorCode;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.IndicatorComponent;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.RiskLevel;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.SignalStrength;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.DailyBarPoint;
import com.minhnb.finvera_be.stock.domain.screener.ScreenerV1.IndicatorSnapshot;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.EntryEvaluation;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.EntryStatus;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.RiskAssessment;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.RiskFactorResult;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.StrategyInputs;
import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import com.minhnb.finvera_be.stock.entity.StrategySignalEntity;
import com.minhnb.finvera_be.stock.entity.StrategySignalInputEntity;
import com.minhnb.finvera_be.stock.entity.StrategySignalRiskFactorEntity;
import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorResultEntity;
import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorValueEntity;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.StrategySignalInputRepository;
import com.minhnb.finvera_be.stock.repository.StrategySignalRepository;
import com.minhnb.finvera_be.stock.repository.StrategySignalRiskFactorRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorResultRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorValueRepository;
import com.minhnb.finvera_be.stock.service.CoherenceKeys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-001 to FR-008, FR-012 to FR-014; DATA-001 to DATA-004; SEC-001, SEC-002
 * (research R-002 to R-006, R-009). Evaluates all eight {@code
 * strategy-signal-v1} strategies for one instrument on every read (cheap,
 * in-memory), persists a triggered signal only when its contributing inputs
 * actually differ from what is already stored for that as-of date — the same
 * idempotent revision-chain pattern {@code ValuationService} uses — and
 * reports every non-triggered/insufficient/withheld strategy live, never
 * persisted (data-model.md "Why non-triggers are not persisted").
 */
@Service
public class StrategySignalService {

    private static final int MAX_BREAKOUT_BARS = 21;
    private static final int CURRENT_AND_PRIOR = 2;

    private final MarketReferenceDataService referenceData;
    private final EquityDailyBarRepository dailyBars;
    private final TechnicalIndicatorResultRepository technicalResults;
    private final TechnicalIndicatorValueRepository technicalValues;
    private final StrategySignalRepository signals;
    private final StrategySignalRiskFactorRepository signalRiskFactors;
    private final StrategySignalInputRepository signalInputs;
    private final RiskFactorInputAssembler riskFactorInputs;
    private final Clock clock;

    public StrategySignalService(
            MarketReferenceDataService referenceData,
            EquityDailyBarRepository dailyBars,
            TechnicalIndicatorResultRepository technicalResults,
            TechnicalIndicatorValueRepository technicalValues,
            StrategySignalRepository signals,
            StrategySignalRiskFactorRepository signalRiskFactors,
            StrategySignalInputRepository signalInputs,
            RiskFactorInputAssembler riskFactorInputs,
            Clock clock) {
        this.referenceData = referenceData;
        this.dailyBars = dailyBars;
        this.technicalResults = technicalResults;
        this.technicalValues = technicalValues;
        this.signals = signals;
        this.signalRiskFactors = signalRiskFactors;
        this.signalInputs = signalInputs;
        this.riskFactorInputs = riskFactorInputs;
        this.clock = clock;
    }

    @Transactional
    public Optional<StockSignals> findBySymbol(String symbol) {
        Optional<InstrumentReference> instrumentOpt = referenceData.findActiveInstrumentBySymbol(symbol);
        if (instrumentOpt.isEmpty()) {
            return Optional.empty();
        }
        UUID instrumentId = instrumentOpt.orElseThrow().instrumentId();

        List<EquityDailyBarEntity> recentBarEntities = dailyBars
                .findLatestNCurrentByInstrumentIdIn(List.of(instrumentId), MAX_BREAKOUT_BARS);
        EquityDailyBarEntity latestBar = recentBarEntities.isEmpty() ? null
                : recentBarEntities.get(recentBarEntities.size() - 1);
        BigDecimal close = latestBar == null ? null : latestBar.getClosePrice();
        LocalDate asOfTradingDate = latestBar == null ? null : latestBar.getTradingDate();
        List<DailyBarPoint> recentBarPoints = recentBarEntities.stream()
                .map(b -> new DailyBarPoint(b.getTradingDate(), b.getClosePrice(), b.getHighPrice(), b.getLowPrice()))
                .toList();

        List<TechnicalIndicatorResultEntity> resultRows = technicalResults
                .findLatestNCurrentByInstrumentIdInAndRuleVersion(List.of(instrumentId),
                        com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.RULE_VERSION,
                        CURRENT_AND_PRIOR);
        List<UUID> allResultIds = resultRows.stream().map(TechnicalIndicatorResultEntity::getId).toList();
        Map<UUID, List<TechnicalIndicatorValueEntity>> valuesByResult = technicalValues.findByResultIdIn(allResultIds)
                .stream().collect(Collectors.groupingBy(TechnicalIndicatorValueEntity::getResultId));

        Map<IndicatorCode, IndicatorSnapshot> current = new EnumMap<>(IndicatorCode.class);
        Map<IndicatorCode, IndicatorSnapshot> prior = new EnumMap<>(IndicatorCode.class);
        Map<IndicatorCode, UUID> currentResultId = new EnumMap<>(IndicatorCode.class);
        Map<IndicatorCode, UUID> priorResultId = new EnumMap<>(IndicatorCode.class);
        for (var entry : resultRows.stream().collect(Collectors.groupingBy(r -> parseIndicatorCode(r.getIndicatorCode())))
                .entrySet()) {
            IndicatorCode code = entry.getKey();
            if (code == null) {
                continue;
            }
            List<TechnicalIndicatorResultEntity> rows = entry.getValue().stream()
                    .sorted(Comparator.comparing(TechnicalIndicatorResultEntity::getAsOfTradingDate).reversed())
                    .toList();
            var currentRow = rows.get(0);
            current.put(code, toSnapshot(currentRow, valuesByResult.getOrDefault(currentRow.getId(), List.of())));
            currentResultId.put(code, currentRow.getId());
            if (rows.size() > 1) {
                var priorRow = rows.get(1);
                prior.put(code, toSnapshot(priorRow, valuesByResult.getOrDefault(priorRow.getId(), List.of())));
                priorResultId.put(code, priorRow.getId());
            }
        }

        StrategyInputs inputs = new StrategyInputs(close, current, prior, recentBarPoints);

        List<StrategyEvaluationResult> evaluations = new ArrayList<>();
        List<String> coherenceParts = new ArrayList<>();
        coherenceParts.add(instrumentId.toString());
        if (latestBar != null) {
            coherenceParts.add(latestBar.getId().toString());
        }

        RiskFactorInputAssembler.RiskContext riskContext = null;
        for (StrategyCode code : StrategyCode.values()) {
            EntryEvaluation eval = StrategySignalV1.evaluate(code, inputs);
            if (eval.status() != EntryStatus.SIGNAL) {
                evaluations.add(new StrategyEvaluationResult(code, toApiStatus(eval.status()), eval.reasonCode(),
                        null));
                continue;
            }
            if (riskContext == null) {
                riskContext = riskFactorInputs.build(instrumentId, current.get(IndicatorCode.ATR14),
                        current.get(IndicatorCode.RELATIVE_VOLUME));
            }
            RiskAssessment risk = StrategySignalV1.computeRisk(close, eval.levels(), riskContext.inputs());
            PersistedSignal persisted = persist(instrumentId, code, asOfTradingDate, clock.instant(), eval, risk,
                    currentResultId, priorResultId, latestBar.getId(), riskContext.regimeAssessmentId());
            coherenceParts.add(persisted.signalId().toString());
            evaluations.add(new StrategyEvaluationResult(code, EvaluationStatus.SIGNAL, null,
                    new SignalDetail(code, StrategySignalV1.RULE_VERSION, eval.direction(), eval.levels(),
                            risk.overallScore(), risk.riskLevel(), risk.signalStrength(), risk.factors(),
                            eval.supportingEvidence(), risk.reasonCodes(), persisted.asOfTradingDate(),
                            persisted.calculatedAt())));
        }

        DataStatus overall = latestBar == null ? DataStatus.UNAVAILABLE : DataStatus.CURRENT;
        String coherenceKey = CoherenceKeys.of(coherenceParts);
        return Optional.of(new StockSignals(symbol, overall, evaluations, coherenceKey, clock.instant()));
    }

    // ── Persistence (revision chain, idempotent on unchanged inputs) ────────

    private PersistedSignal persist(UUID instrumentId, StrategyCode code, LocalDate asOfTradingDate,
            Instant calculatedAt, EntryEvaluation eval, RiskAssessment risk, Map<IndicatorCode, UUID> currentResultId,
            Map<IndicatorCode, UUID> priorResultId, UUID dailyBarId, UUID regimeAssessmentId) {
        List<String> hashParts = new ArrayList<>();
        for (IndicatorCode ic : StrategySignalV1.currentIndicatorsFor(code)) {
            UUID id = currentResultId.get(ic);
            if (id != null) {
                hashParts.add("CUR:" + ic + ":" + id);
            }
        }
        for (IndicatorCode ic : StrategySignalV1.priorIndicatorsFor(code)) {
            UUID id = priorResultId.get(ic);
            if (id != null) {
                hashParts.add("PRI:" + ic + ":" + id);
            }
        }
        hashParts.add("BAR:" + dailyBarId);
        if (regimeAssessmentId != null) {
            hashParts.add("REGIME:" + regimeAssessmentId);
        }
        String inputSetHash = sha256Hex(String.join("|", hashParts));

        Optional<StrategySignalEntity> existing = signals
                .findFirstByInstrumentIdAndStrategyCodeAndAsOfTradingDateAndRuleVersionAndCurrentTrue(
                        instrumentId, code.name(), asOfTradingDate, StrategySignalV1.RULE_VERSION);
        if (existing.isPresent() && inputSetHash.equals(existing.get().getInputSetHash())) {
            var e = existing.get();
            return new PersistedSignal(e.getId(), e.getAsOfTradingDate(), e.getCalculatedAt());
        }
        if (existing.isPresent()) {
            var previous = existing.get();
            previous.markSuperseded();
            signals.saveAndFlush(previous);
        }

        UUID signalId = UUID.randomUUID();
        var levels = eval.levels();
        signals.save(new StrategySignalEntity(signalId, instrumentId, code.name(), StrategySignalV1.RULE_VERSION,
                asOfTradingDate, eval.direction().name(), levels.entryLow(), levels.entryHigh(), levels.stopLoss(),
                levels.target1(), levels.target2(), levels.riskReward(), risk.overallScore(),
                risk.riskLevel() == null ? null : risk.riskLevel().name(), inputSetHash, calculatedAt, true,
                existing.map(StrategySignalEntity::getId).orElse(null)));

        for (RiskFactorResult factor : risk.factors()) {
            signalRiskFactors.save(new StrategySignalRiskFactorEntity(signalId, factor.factorCode().name(),
                    factor.inputValue(), factor.factorScore(), factor.applicability().name(), factor.reasonCode()));
        }

        for (IndicatorCode ic : StrategySignalV1.currentIndicatorsFor(code)) {
            UUID id = currentResultId.get(ic);
            if (id != null) {
                signalInputs.save(new StrategySignalInputEntity(signalId, "TECHNICAL_RESULT_CURRENT_" + ic, id,
                        null, null));
            }
        }
        for (IndicatorCode ic : StrategySignalV1.priorIndicatorsFor(code)) {
            UUID id = priorResultId.get(ic);
            if (id != null) {
                signalInputs.save(new StrategySignalInputEntity(signalId, "TECHNICAL_RESULT_PRIOR_" + ic, id, null,
                        null));
            }
        }
        signalInputs.save(new StrategySignalInputEntity(signalId, "DAILY_BAR_CURRENT", null, dailyBarId, null));
        if (regimeAssessmentId != null) {
            signalInputs.save(new StrategySignalInputEntity(signalId, "REGIME_ASSESSMENT", null, null,
                    regimeAssessmentId));
        }
        return new PersistedSignal(signalId, asOfTradingDate, calculatedAt);
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

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

    private static EvaluationStatus toApiStatus(EntryStatus status) {
        return switch (status) {
            case NO_SIGNAL -> EvaluationStatus.NO_SIGNAL;
            case INSUFFICIENT_HISTORY -> EvaluationStatus.INSUFFICIENT_HISTORY;
            case WITHHELD -> EvaluationStatus.WITHHELD;
            case SIGNAL -> EvaluationStatus.SIGNAL;
        };
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    // ── Result types ──────────────────────────────────────────────────────────

    public enum EvaluationStatus {
        SIGNAL, NO_SIGNAL, INSUFFICIENT_HISTORY, WITHHELD
    }

    private record PersistedSignal(UUID signalId, LocalDate asOfTradingDate, Instant calculatedAt) {
    }

    public record SignalDetail(
            StrategyCode strategyCode,
            String ruleVersion,
            com.minhnb.finvera_be.stock.domain.model.StockTypes.Direction direction,
            StrategySignalV1.LevelSet levels,
            Integer riskScore,
            RiskLevel riskLevel,
            SignalStrength signalStrength,
            List<RiskFactorResult> riskFactors,
            Map<String, String> supportingEvidence,
            List<String> reasonCodes,
            LocalDate asOfTradingDate,
            Instant calculatedAt) {
    }

    public record StrategyEvaluationResult(StrategyCode strategyCode, EvaluationStatus status, String reasonCode,
            SignalDetail signal) {
    }

    public record StockSignals(String symbol, DataStatus dataStatus, List<StrategyEvaluationResult> evaluations,
            String coherenceKey, Instant asOf) {
    }
}
