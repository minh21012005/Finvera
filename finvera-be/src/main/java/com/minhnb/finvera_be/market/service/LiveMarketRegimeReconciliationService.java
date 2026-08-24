package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.regime.MarketRegimeV1;
import com.minhnb.finvera_be.market.domain.regime.RegimeAssessment;
import com.minhnb.finvera_be.market.entity.MarketIndexSnapshotEntity;
import com.minhnb.finvera_be.market.repository.MarketIndexRepository;
import com.minhnb.finvera_be.market.repository.MarketIndexSnapshotRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Reconciles accepted live facts into an immutable, explainable market-regime assessment. */
@Service
public class LiveMarketRegimeReconciliationService {
    private static final String DEPRECATED_SOURCE = "TCBS_IFLASH_MARKET_DATA";
    private static final MathContext CONTEXT = MathContext.DECIMAL128;
    private final MarketIndexRepository indexes;
    private final MarketIndexSnapshotRepository snapshots;
    private final RegimeAssessmentService assessments;
    private final MarketRegimeV1 rule = new MarketRegimeV1();

    public LiveMarketRegimeReconciliationService(MarketIndexRepository indexes,
            MarketIndexSnapshotRepository snapshots, RegimeAssessmentService assessments) {
        this.indexes = indexes;
        this.snapshots = snapshots;
        this.assessments = assessments;
    }

    /**
     * Reconciles only persisted facts. A provider aggregate does not contain the
     * full-universe SMA50 coverage needed by the approved breadth component.
     */
    public void reconcile(LocalDate tradingDate, BreadthService.Snapshot breadth) {
        Objects.requireNonNull(tradingDate, "tradingDate");
        Objects.requireNonNull(breadth, "breadth");
        var index = indexes.findByCode("VN_INDEX").orElseThrow(
                () -> new IllegalStateException("VN_INDEX reference is required"));
        List<MarketIndexSnapshotEntity> history = snapshots.findAcceptedDailyHistory(
                index.getId(), DEPRECATED_SOURCE, tradingDate);
        if (history.isEmpty() || !history.getLast().getTradingDate().equals(tradingDate)) {
            return;
        }
        MarketIndexSnapshotEntity current = history.getLast();
        List<MarketRegimeV1.ComponentScore> componentScores = componentScores(history);
        RegimeAssessment calculated = rule.assess(componentScores,
                new MarketRegimeV1.InputAvailability(true, true, DataStatus.CURRENT, breadth.dataStatus()));
        RegimeAssessment assessment = withAvailabilityReasons(calculated, history, current);
        Instant asOf = current.getObservedAt().isAfter(breadth.asOf()) ? current.getObservedAt() : breadth.asOf();
        assessments.persist(new RegimeAssessmentService.AssessmentCommand(tradingDate, asOf, assessment, null,
                List.of(RegimeAssessmentService.InputLink.indexSnapshot("VN_INDEX_CURRENT", current.getId()),
                        RegimeAssessmentService.InputLink.breadthSnapshot("BREADTH_CURRENT", breadth.id()),
                        RegimeAssessmentService.InputLink.inputSet("VN_INDEX_DAILY_HISTORY", historyHash(history))),
                List.of(new RegimeAssessmentService.SourceValue(current.getSource(), "VN_INDEX_CURRENT",
                        current.getIndexLevel()))));
    }

    private List<MarketRegimeV1.ComponentScore> componentScores(List<MarketIndexSnapshotEntity> history) {
        List<BigDecimal> closes = history.stream().map(MarketIndexSnapshotEntity::getIndexLevel).toList();
        List<MarketRegimeV1.ComponentScore> scores = new ArrayList<>();
        int size = closes.size();
        if (size >= 220) {
            scores.add(new MarketRegimeV1.ComponentScore(MarketRegimeV1.Component.TREND,
                    rule.trendScore(new MarketRegimeV1.TrendInput(closes.getLast(), mean(closes, size - 20, size),
                            mean(closes, size - 50, size), mean(closes, size - 200, size),
                            mean(closes, size - 40, size - 20)))));
        }
        if (size >= 21) {
            BigDecimal return20 = closes.getLast().divide(closes.get(size - 21), CONTEXT).subtract(BigDecimal.ONE);
            scores.add(new MarketRegimeV1.ComponentScore(MarketRegimeV1.Component.MOMENTUM,
                    rule.momentumScore(wilderRsi14(closes), return20)));
        }
        if (size >= 253) {
            scores.add(new MarketRegimeV1.ComponentScore(MarketRegimeV1.Component.VOLATILITY,
                    rule.volatilityScore(volatilityPercentile(closes.subList(size - 253, size)))));
        }
        return scores;
    }

    private static RegimeAssessment withAvailabilityReasons(RegimeAssessment calculated,
            List<MarketIndexSnapshotEntity> history, MarketIndexSnapshotEntity current) {
        List<String> reasons = new ArrayList<>(calculated.reasonCodes());
        addIfMissing(reasons, "BREADTH_SMA50_COVERAGE_UNAVAILABLE");
        boolean hasLiquidityHistory = history.size() >= 21 && history.subList(history.size() - 21, history.size())
                .stream().allMatch(snapshot -> snapshot.getMatchedValueVnd() != null);
        if (!hasLiquidityHistory || current.getMatchedValueVnd() == null) {
            addIfMissing(reasons, "LIQUIDITY_HISTORY_UNAVAILABLE");
        }
        return new RegimeAssessment(calculated.dataStatus(), calculated.label(), calculated.score(),
                calculated.confidence(), calculated.completeness(), calculated.factorAgreement(),
                calculated.boundaryDistance(), calculated.renormalized(), reasons, calculated.factors());
    }

    private static void addIfMissing(List<String> reasons, String reason) {
        if (!reasons.contains(reason)) reasons.add(reason);
    }

    private static BigDecimal mean(List<BigDecimal> values, int from, int to) {
        return values.subList(from, to).stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(to - from), CONTEXT);
    }

    private static BigDecimal wilderRsi14(List<BigDecimal> closes) {
        int start = Math.max(1, closes.size() - 14);
        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;
        for (int i = start; i < closes.size(); i++) {
            BigDecimal change = closes.get(i).subtract(closes.get(i - 1));
            if (change.signum() >= 0) gains = gains.add(change); else losses = losses.add(change.abs());
        }
        if (losses.signum() == 0) return BigDecimal.valueOf(100);
        BigDecimal relativeStrength = gains.divide(BigDecimal.valueOf(14), CONTEXT)
                .divide(losses.divide(BigDecimal.valueOf(14), CONTEXT), CONTEXT);
        return BigDecimal.valueOf(100).subtract(BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(relativeStrength), CONTEXT));
    }

    private static BigDecimal volatilityPercentile(List<BigDecimal> closes) {
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < closes.size(); i++) returns.add(closes.get(i).divide(closes.get(i - 1), CONTEXT).subtract(BigDecimal.ONE));
        List<BigDecimal> volatilities = new ArrayList<>();
        for (int end = 20; end <= returns.size(); end++) volatilities.add(populationStandardDeviation(returns.subList(end - 20, end)));
        BigDecimal current = volatilities.getLast();
        long lower = volatilities.stream().filter(value -> value.compareTo(current) < 0).count();
        long equal = volatilities.stream().filter(value -> value.compareTo(current) == 0).count();
        return BigDecimal.valueOf(lower).add(BigDecimal.valueOf(equal).subtract(BigDecimal.ONE).divide(BigDecimal.valueOf(2), CONTEXT))
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(volatilities.size()), CONTEXT);
    }

    private static BigDecimal populationStandardDeviation(List<BigDecimal> values) {
        BigDecimal average = mean(values, 0, values.size());
        BigDecimal variance = values.stream().map(value -> value.subtract(average).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()), CONTEXT);
        return variance.sqrt(CONTEXT);
    }

    private static String historyHash(List<MarketIndexSnapshotEntity> history) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (MarketIndexSnapshotEntity value : history) {
                digest.update((value.getId() + "|" + value.getTradingDate() + "|" + value.getIndexLevel() + "\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
