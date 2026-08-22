package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.stock.domain.chart.StockChartAssembler;
import com.minhnb.finvera_be.stock.domain.chart.StockChartAssembler.BarInput;
import com.minhnb.finvera_be.stock.domain.chart.StockChartAssembler.ChartResult;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.AdjustmentStatus;
import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-003; DATA-008; research R-004, R-009. */
@Service
public class StockChartService {

    private static final Map<String, Long> WINDOW_DAYS = Map.of(
            "1M", 31L, "3M", 93L, "6M", 186L, "1Y", 366L, "2Y", 732L, "ALL", 3650L);
    // Preference order used only to deduplicate a same trading-date conflict
    // between two accepted sources (DATA-010); it does not decide correctness,
    // it just keeps the chart series single-valued per date. Full conflict
    // withholding is the technical/valuation layer's responsibility.
    private static final List<String> SOURCE_PREFERENCE = List.of("TCBS", "VNSTOCK", "FINVERA_FIXTURE");

    private final MarketReferenceDataService referenceData;
    private final EquityDailyBarRepository dailyBars;
    private final StockChartAssembler assembler = new StockChartAssembler();
    private final Clock clock;

    public StockChartService(MarketReferenceDataService referenceData, EquityDailyBarRepository dailyBars, Clock clock) {
        this.referenceData = referenceData;
        this.dailyBars = dailyBars;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<StockChart> findBySymbol(String symbol, String window) {
        var instrument = referenceData.findActiveInstrumentBySymbol(symbol);
        if (instrument.isEmpty()) {
            return Optional.empty();
        }
        long lookbackDays = WINDOW_DAYS.getOrDefault(window, WINDOW_DAYS.get("ALL"));
        Instant asOf = clock.instant();
        LocalDate toDate = asOf.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
        LocalDate fromDate = toDate.minusDays(lookbackDays);

        List<EquityDailyBarEntity> rows = dailyBars.findByInstrumentIdAndCurrentTrueAndTradingDateBetweenOrderByTradingDateAsc(
                instrument.orElseThrow().instrumentId(), fromDate, toDate);
        List<EquityDailyBarEntity> deduplicated = dedupeByTradingDate(rows);

        List<BarInput> inputs = deduplicated.stream().map(StockChartService::toBarInput).toList();
        ChartResult result = assembler.assemble(inputs, window);

        DataStatus dataStatus = result.bars().isEmpty() ? DataStatus.UNAVAILABLE : DataStatus.CURRENT;
        String coherenceKey = CoherenceKeys.of(deduplicated.stream()
                .map(bar -> bar.getId() + ":" + bar.getRevision()).toList());

        return Optional.of(new StockChart(window, result.seriesAdjustmentStatus(), result.bars(),
                dataStatus, result.reasonCode(), asOf, coherenceKey));
    }

    private static List<EquityDailyBarEntity> dedupeByTradingDate(List<EquityDailyBarEntity> rows) {
        Map<LocalDate, EquityDailyBarEntity> byDate = new LinkedHashMap<>();
        for (EquityDailyBarEntity row : rows) {
            byDate.merge(row.getTradingDate(), row, StockChartService::preferred);
        }
        return byDate.values().stream().sorted(Comparator.comparing(EquityDailyBarEntity::getTradingDate)).toList();
    }

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

    public record StockChart(
            String window,
            AdjustmentStatus adjustmentStatus,
            List<StockChartAssembler.ChartBar> bars,
            DataStatus dataStatus,
            String reasonCode,
            Instant asOf,
            String coherenceKey) {
    }
}
