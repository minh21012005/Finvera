package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.reconciliation.SourceReconciliationPolicy.Decision;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.domain.chart.StockChartAssembler;
import com.minhnb.finvera_be.stock.domain.chart.StockChartAssembler.BarInput;
import com.minhnb.finvera_be.stock.domain.chart.StockChartAssembler.ChartBar;
import com.minhnb.finvera_be.stock.domain.chart.StockChartAssembler.ChartResult;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.AdjustmentStatus;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.IndicatorResult;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.IndicatorSetResult;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.TechnicalBar;
import com.minhnb.finvera_be.stock.domain.time.StockFreshnessPolicy;
import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorResultEntity;
import com.minhnb.finvera_be.stock.entity.TechnicalIndicatorValueEntity;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorResultRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorValueRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-005, FR-006, FR-011, FR-014; DATA-009, DATA-010; NFR-003. Computes
 * {@code technical-indicators-v1} over the instrument's accepted daily bars
 * and persists each indicator's outcome as an immutable, revision-chained
 * result — recomputing on every read but only writing a new current row when
 * the freshly computed {@code input_set_hash} or reason differs from what is
 * already stored, so an unchanged result never grows a needless revision.
 *
 * <p>DATA-010: an indicator is withheld with {@code SOURCE_CONFLICT} when any
 * bar its own window actually consumed has more than one accepted source and
 * those sources materially disagree, reusing
 * {@code SourceReconciliationService} (via {@link StockIngestionService}) —
 * the same policy T017/T018 already apply to daily bars. Only trading dates
 * carrying more than one source are checked, not every date in the window,
 * so a normal single-sourced history costs no extra query.
 */
@Service
public class TechnicalIndicatorService {

    private static final String CONFLICT_REASON = "SOURCE_CONFLICT";
    // input_set_hash is NOT NULL in the schema even though an unavailable
    // indicator consumed no bars; the SHA-256 of an empty input is a
    // deterministic, honest "nothing was consumed" sentinel, distinguishable
    // from any real (non-empty) consumed-bar hash.
    private static final String EMPTY_INPUT_SET_HASH = sha256Hex("");

    private final MarketReferenceDataService referenceData;
    private final EquityDailyBarRepository dailyBars;
    private final TechnicalIndicatorResultRepository results;
    private final TechnicalIndicatorValueRepository values;
    private final StockIngestionService ingestion;
    private final StockChartAssembler assembler = new StockChartAssembler();
    private final TechnicalIndicatorsV1 engine = new TechnicalIndicatorsV1();
    private final StockFreshnessPolicy freshnessPolicy = new StockFreshnessPolicy();
    private final Clock clock;

    public TechnicalIndicatorService(
            MarketReferenceDataService referenceData,
            EquityDailyBarRepository dailyBars,
            TechnicalIndicatorResultRepository results,
            TechnicalIndicatorValueRepository values,
            StockIngestionService ingestion,
            Clock clock) {
        this.referenceData = referenceData;
        this.dailyBars = dailyBars;
        this.results = results;
        this.values = values;
        this.ingestion = ingestion;
        this.clock = clock;
    }

    @Transactional
    public Optional<StockTechnical> findBySymbol(String symbol) {
        Optional<InstrumentReference> instrument = referenceData.findActiveInstrumentBySymbol(symbol);
        if (instrument.isEmpty()) {
            return Optional.empty();
        }
        InstrumentReference reference = instrument.orElseThrow();
        UUID instrumentId = reference.instrumentId();

        List<EquityDailyBarEntity> allSourceBars = dailyBars.findByInstrumentIdAndCurrentTrueOrderByTradingDateAsc(
                instrumentId);
        List<EquityDailyBarEntity> deduplicated = dedupeByTradingDate(allSourceBars);
        Set<LocalDate> conflictedDates = conflictedTradingDates(instrumentId, symbol, allSourceBars);

        List<BarInput> barInputs = deduplicated.stream().map(TechnicalIndicatorService::toBarInput).toList();
        ChartResult chartResult = assembler.assemble(barInputs, "N/A");
        List<TechnicalBar> technicalBars = chartResult.bars().stream().map(TechnicalIndicatorService::toTechnicalBar)
                .toList();

        IndicatorSetResult computed = engine.compute(technicalBars);
        List<IndicatorResult> withheld = computed.indicators().stream()
                .map(indicator -> withholdOnConflict(indicator, conflictedDates)).toList();

        Instant asOf = clock.instant();
        var session = referenceData.resolveSession(reference.venue(), asOf);
        LocalDate asOfTradingDate = technicalBars.isEmpty() ? null : technicalBars.getLast().tradingDate();
        DataStatus dataStatus = evaluateFreshness(asOfTradingDate, session.tradingDate());

        if (asOfTradingDate != null) {
            for (IndicatorResult indicator : withheld) {
                persistIndicatorResult(instrumentId, asOfTradingDate, indicator, chartResult.seriesAdjustmentStatus(),
                        dataStatus, asOf);
            }
        }

        List<String> reasonCodes = new ArrayList<>();
        if (chartResult.reasonCode() != null) {
            reasonCodes.add(chartResult.reasonCode());
        }
        if (technicalBars.isEmpty()) {
            reasonCodes.add("INSUFFICIENT_HISTORY");
        }

        String coherenceKey = CoherenceKeys.of(deduplicated.stream()
                .map(bar -> bar.getId() + ":" + bar.getRevision()).toList());

        return Optional.of(new StockTechnical(TechnicalIndicatorsV1.RULE_VERSION, chartResult.seriesAdjustmentStatus(),
                withheld, dataStatus, List.copyOf(reasonCodes), asOfTradingDate, asOf, coherenceKey));
    }

    private Set<LocalDate> conflictedTradingDates(UUID instrumentId, String symbol,
            List<EquityDailyBarEntity> allSourceBars) {
        Map<LocalDate, Set<String>> sourcesByDate = new LinkedHashMap<>();
        for (EquityDailyBarEntity bar : allSourceBars) {
            sourcesByDate.computeIfAbsent(bar.getTradingDate(), d -> new LinkedHashSet<>()).add(bar.getSource());
        }
        Set<LocalDate> conflicted = new LinkedHashSet<>();
        for (var entry : sourcesByDate.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            Decision decision = ingestion.reconcileDailyBar(instrumentId, symbol, entry.getKey());
            if (decision == Decision.SOURCE_CONFLICT) {
                conflicted.add(entry.getKey());
            }
        }
        return conflicted;
    }

    private static IndicatorResult withholdOnConflict(IndicatorResult indicator, Set<LocalDate> conflictedDates) {
        if (conflictedDates.isEmpty() || indicator.windowStartDate() == null || indicator.windowEndDate() == null) {
            return indicator;
        }
        boolean conflictInWindow = conflictedDates.stream()
                .anyMatch(date -> !date.isBefore(indicator.windowStartDate()) && !date.isAfter(indicator.windowEndDate()));
        if (!conflictInWindow) {
            return indicator;
        }
        return new IndicatorResult(indicator.indicatorCode(), MetricApplicability.MISSING, indicator.requiredBars(),
                indicator.availableBars(), null, null, 0, null, CONFLICT_REASON, List.of());
    }

    private void persistIndicatorResult(UUID instrumentId, LocalDate asOfTradingDate, IndicatorResult computed,
            AdjustmentStatus seriesAdjustmentStatus, DataStatus dataStatus, Instant calculatedAt) {
        String code = computed.indicatorCode().name();
        Optional<TechnicalIndicatorResultEntity> existing = results
                .findFirstByInstrumentIdAndIndicatorCodeAndAsOfTradingDateAndRuleVersionAndCurrentTrue(
                        instrumentId, code, asOfTradingDate, TechnicalIndicatorsV1.RULE_VERSION);
        // window_start_date/window_end_date/input_set_hash are NOT NULL in the
        // schema even though the API contract allows them to be null (an
        // unavailable indicator consumed no real window). The as-of date and the
        // empty-input hash stand in as honest, deterministic sentinels — the DTO
        // still reports the API-facing value as null via the domain result, not
        // this persisted substitution.
        LocalDate windowStart = computed.windowStartDate() != null ? computed.windowStartDate() : asOfTradingDate;
        LocalDate windowEnd = computed.windowEndDate() != null ? computed.windowEndDate() : asOfTradingDate;
        String inputSetHash = computed.inputSetHash() != null ? computed.inputSetHash() : EMPTY_INPUT_SET_HASH;

        boolean unchanged = existing.isPresent()
                && Objects.equals(existing.orElseThrow().getInputSetHash(), inputSetHash)
                && Objects.equals(existing.orElseThrow().getQualityReason(), computed.reasonCode());
        if (unchanged) {
            return;
        }

        if (existing.isPresent()) {
            TechnicalIndicatorResultEntity previous = existing.orElseThrow();
            previous.markSuperseded();
            results.saveAndFlush(previous);
        }

        UUID resultId = UUID.randomUUID();
        results.save(new TechnicalIndicatorResultEntity(resultId, instrumentId, code,
                TechnicalIndicatorsV1.RULE_VERSION, asOfTradingDate, windowStart, windowEnd,
                computed.inputBarCount(), inputSetHash, seriesAdjustmentStatus.name(), dataStatus.name(),
                computed.reasonCode(), calculatedAt, true,
                existing.map(TechnicalIndicatorResultEntity::getId).orElse(null)));
        for (var component : computed.components()) {
            values.save(new TechnicalIndicatorValueEntity(resultId, component.componentCode().name(),
                    component.value(), component.unit().name(), component.applicability().name(),
                    component.reasonCode()));
        }
    }

    private DataStatus evaluateFreshness(LocalDate asOfTradingDate, LocalDate sessionTradingDate) {
        if (asOfTradingDate == null) {
            return freshnessPolicy.evaluateMissing();
        }
        int sessionsBehind = countWeekdaysBetween(asOfTradingDate, sessionTradingDate);
        return freshnessPolicy.evaluateDailyBarSeries(sessionsBehind);
    }

    /** Same weekday-count approximation as {@link StockOverviewService}; see its Javadoc for the caveat. */
    private static int countWeekdaysBetween(LocalDate lastAccepted, LocalDate asOfTradingDate) {
        int count = 0;
        LocalDate cursor = lastAccepted;
        while (cursor.isBefore(asOfTradingDate)) {
            cursor = cursor.plusDays(1);
            if (cursor.getDayOfWeek() != DayOfWeek.SATURDAY && cursor.getDayOfWeek() != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return count;
    }

    private static List<EquityDailyBarEntity> dedupeByTradingDate(List<EquityDailyBarEntity> rows) {
        Map<LocalDate, EquityDailyBarEntity> byDate = new LinkedHashMap<>();
        for (EquityDailyBarEntity row : rows) {
            byDate.merge(row.getTradingDate(), row, TechnicalIndicatorService::preferred);
        }
        return byDate.values().stream().sorted(Comparator.comparing(EquityDailyBarEntity::getTradingDate)).toList();
    }

    // Same source preference as StockChartService: only decides which single
    // accepted row represents a same-date multi-source conflict in the series
    // this engine consumes; it does not decide whether that date is flagged.
    private static final List<String> SOURCE_PREFERENCE = List.of("TCBS", "VNSTOCK", "FINVERA_FIXTURE");

    private static EquityDailyBarEntity preferred(EquityDailyBarEntity left, EquityDailyBarEntity right) {
        int leftRank = SOURCE_PREFERENCE.indexOf(left.getSource());
        int rightRank = SOURCE_PREFERENCE.indexOf(right.getSource());
        if (leftRank == -1) leftRank = SOURCE_PREFERENCE.size();
        if (rightRank == -1) rightRank = SOURCE_PREFERENCE.size();
        return leftRank <= rightRank ? left : right;
    }

    private static BarInput toBarInput(EquityDailyBarEntity entity) {
        AdjustmentStatus status = "ADJUSTED".equals(entity.getAdjustmentStatus())
                ? AdjustmentStatus.ADJUSTED
                : "RAW".equals(entity.getAdjustmentStatus()) ? AdjustmentStatus.RAW : AdjustmentStatus.UNKNOWN;
        return new BarInput(entity.getTradingDate(), entity.getOpenPrice(), entity.getHighPrice(),
                entity.getLowPrice(), entity.getClosePrice(), entity.getAdjustedClose(),
                entity.getAdjustmentFactor(), entity.getVolume(), status);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static TechnicalBar toTechnicalBar(ChartBar bar) {
        return new TechnicalBar(bar.tradingDate(), bar.close(), bar.high(), bar.low(),
                bar.volume() == null ? 0L : bar.volume());
    }

    public record StockTechnical(
            String ruleVersion,
            AdjustmentStatus adjustmentStatus,
            List<IndicatorResult> indicators,
            DataStatus dataStatus,
            List<String> reasonCodes,
            LocalDate tradingDate,
            Instant asOf,
            String coherenceKey) {
    }
}
