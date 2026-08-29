package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.regime.MarketRegimeV1;
import com.minhnb.finvera_be.market.domain.regime.MarketRegimeV2;
import com.minhnb.finvera_be.market.domain.regime.RegimeAssessment;
import com.minhnb.finvera_be.market.domain.regime.math.DecimalTimeSeries;
import com.minhnb.finvera_be.market.entity.MarketIndexSnapshotEntity;
import com.minhnb.finvera_be.market.repository.MarketIndexRepository;
import com.minhnb.finvera_be.market.repository.MarketIndexSnapshotRepository;
import java.math.BigDecimal;
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
    private static final String COMPLETED_SESSION_SOURCE_PREFIX = "VNSTOCK%";
    private final MarketIndexRepository indexes;
    private final MarketIndexSnapshotRepository snapshots;
    private final RegimeAssessmentService assessments;
    private final MarketRegimeV1 scoreFunctions = new MarketRegimeV1();
    private final MarketRegimeV2 rule = new MarketRegimeV2();
    private final BigDecimal minBreadthClassifiedFraction;

    /** Test/fixture convenience: the default 0.5 classified-coverage floor. */
    public LiveMarketRegimeReconciliationService(MarketIndexRepository indexes,
            MarketIndexSnapshotRepository snapshots, RegimeAssessmentService assessments) {
        this(indexes, snapshots, assessments, new BigDecimal("0.5"));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LiveMarketRegimeReconciliationService(MarketIndexRepository indexes,
            MarketIndexSnapshotRepository snapshots, RegimeAssessmentService assessments,
            @org.springframework.beans.factory.annotation.Value(
                    "${finvera.market.regime.min-breadth-classified-fraction:0.5}")
            BigDecimal minBreadthClassifiedFraction) {
        this.indexes = indexes;
        this.snapshots = snapshots;
        this.assessments = assessments;
        if (minBreadthClassifiedFraction == null || minBreadthClassifiedFraction.signum() < 0
                || minBreadthClassifiedFraction.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("min-breadth-classified-fraction must be within [0, 1]");
        }
        this.minBreadthClassifiedFraction = minBreadthClassifiedFraction;
    }

    /**
     * Reconciles only persisted facts. Live provider frames are normalized into
     * PostgreSQL before they can influence a versioned regime assessment.
     */
    public void reconcileIfMissingOrOlder(LocalDate tradingDate, BreadthService.Snapshot breadth) {
        Objects.requireNonNull(tradingDate, "tradingDate");
        Objects.requireNonNull(breadth, "breadth");
        var latest = assessments.latestFor(tradingDate, "LIVE");
        if (latest.isPresent() && coversBreadthWithPublishedV2(latest.orElseThrow(), breadth, "LIVE")) {
            return;
        }
        reconcileLive(tradingDate, breadth);
    }

    public void reconcileEndOfDayIfMissingOrOlder(LocalDate tradingDate, BreadthService.Snapshot breadth) {
        Objects.requireNonNull(tradingDate, "tradingDate");
        Objects.requireNonNull(breadth, "breadth");
        var latest = assessments.latestFor(tradingDate, "EOD");
        if (latest.isPresent() && coversBreadthWithPublishedV2(latest.orElseThrow(), breadth, "EOD")) {
            return;
        }
        reconcileEndOfDay(tradingDate, breadth);
    }

    private boolean coversBreadthWithPublishedV2(
            RegimeAssessmentService.Snapshot latest, BreadthService.Snapshot breadth, String assessmentBasis) {
        return MarketRegimeV2.RULE_VERSION.equals(latest.ruleVersion())
                && assessmentBasis.equals(latest.assessmentBasis())
                && !latest.asOf().isBefore(breadth.asOf())
                && latest.assessment().label() != null
                && latest.assessment().score() != null
                && latest.assessment().confidence() != null
                && latest.assessment().dataStatus() == breadth.dataStatus()
                && latest.assessment().reasonCodes().equals(breadth.result().reasonCodes())
                && assessments.usesBreadthSnapshot(latest, breadth.id())
                && latest.assessment().factors().stream()
                        .anyMatch(factor -> factor.component() == MarketRegimeV1.Component.BREADTH);
    }

    public void reconcile(LocalDate tradingDate, BreadthService.Snapshot breadth) {
        reconcileLive(tradingDate, breadth);
    }

    public void reconcileLive(LocalDate tradingDate, BreadthService.Snapshot breadth) {
        Objects.requireNonNull(tradingDate, "tradingDate");
        Objects.requireNonNull(breadth, "breadth");
        var index = indexes.findByCode("VN_INDEX").orElseThrow(
                () -> new IllegalStateException("VN_INDEX reference is required"));
        List<MarketIndexSnapshotEntity> history = snapshots.findAcceptedDailyHistory(
                index.getId(), DEPRECATED_SOURCE, tradingDate);
        reconcile(tradingDate, breadth, history, "LIVE");
    }

    public void reconcileEndOfDay(LocalDate tradingDate, BreadthService.Snapshot breadth) {
        Objects.requireNonNull(tradingDate, "tradingDate");
        Objects.requireNonNull(breadth, "breadth");
        var index = indexes.findByCode("VN_INDEX").orElseThrow(
                () -> new IllegalStateException("VN_INDEX reference is required"));
        List<MarketIndexSnapshotEntity> history = snapshots.findAcceptedCompletedDailyHistory(
                index.getId(), COMPLETED_SESSION_SOURCE_PREFIX, tradingDate);
        reconcile(tradingDate, breadth, history, "EOD");
    }

    private void reconcile(LocalDate tradingDate, BreadthService.Snapshot breadth,
            List<MarketIndexSnapshotEntity> history, String assessmentBasis) {
        if (history.isEmpty() || !history.getLast().getTradingDate().equals(tradingDate)) {
            return;
        }
        MarketIndexSnapshotEntity current = history.getLast();
        List<MarketRegimeV1.ComponentScore> componentScores = componentScores(history, breadth);
        RegimeAssessment calculated = rule.assess(componentScores,
                new MarketRegimeV2.InputAvailability(true, true, DataStatus.CURRENT, breadth.dataStatus()));
        Instant asOf = current.getObservedAt().isAfter(breadth.asOf()) ? current.getObservedAt() : breadth.asOf();
        assessments.persist(new RegimeAssessmentService.AssessmentCommand(tradingDate, asOf, MarketRegimeV2.RULE_VERSION,
                calculated, assessmentBasis, null,
                List.of(RegimeAssessmentService.InputLink.indexSnapshot("VN_INDEX_CURRENT", current.getId()),
                        RegimeAssessmentService.InputLink.breadthSnapshot("BREADTH_CURRENT", breadth.id()),
                        RegimeAssessmentService.InputLink.inputSet("VN_INDEX_DAILY_HISTORY", historyHash(history))),
                List.of(new RegimeAssessmentService.SourceValue(current.getSource(), "VN_INDEX_CURRENT",
                        current.getIndexLevel()))));
    }

    private List<MarketRegimeV1.ComponentScore> componentScores(List<MarketIndexSnapshotEntity> history,
            BreadthService.Snapshot breadth) {
        List<BigDecimal> closes = history.stream().map(MarketIndexSnapshotEntity::getIndexLevel).toList();
        List<MarketRegimeV1.ComponentScore> scores = new ArrayList<>();
        int size = closes.size();
        if (size >= 220) {
            scores.add(new MarketRegimeV1.ComponentScore(MarketRegimeV1.Component.TREND,
                    scoreFunctions.trendScore(new MarketRegimeV1.TrendInput(closes.getLast(),
                            DecimalTimeSeries.sma(closes, 20), DecimalTimeSeries.sma(closes, 50),
                            DecimalTimeSeries.sma(closes, 200), smaEndingAt(closes, size - 20, 20)))));
        }
        if (size >= 21) {
            BigDecimal return20 = DecimalTimeSeries.simpleReturn(closes.getLast(), closes.get(size - 21));
            scores.add(new MarketRegimeV1.ComponentScore(MarketRegimeV1.Component.MOMENTUM,
                    scoreFunctions.momentumScore(DecimalTimeSeries.wilderRsi(closes, 14), return20)));
        }
        if (size >= 253) {
            scores.add(new MarketRegimeV1.ComponentScore(MarketRegimeV1.Component.VOLATILITY,
                    scoreFunctions.volatilityScore(volatilityPercentile(closes.subList(size - 253, size)))));
        }
        int advancing = breadth.result().advancing();
        int declining = breadth.result().declining();
        if (advancing + declining > 0 && meetsClassifiedCoverageFloor(breadth.result())) {
            scores.add(new MarketRegimeV1.ComponentScore(MarketRegimeV1.Component.BREADTH,
                    rule.aggregateBreadthScore(advancing, declining)));
        }
        return scores;
    }

    /**
     * The BREADTH component carries weight 0.25 of a published regime score, so a
     * sample that classified only a sliver of the eligible universe (one advancing
     * instrument and nothing else would previously have scored BREADTH = 100) must
     * not be admitted as if it represented the market. `unchanged` counts as
     * classified — it is a successful classification, not a gap. The floor is
     * configuration (`finvera.market.regime.min-breadth-classified-fraction`,
     * default 0.5), never a hard-coded constant, per ARCHITECTURE.md section 8.
     * A snapshot below the floor simply withholds the component; market-regime-v2
     * then discloses AGGREGATE_BREADTH_COMPONENT_UNAVAILABLE through its own
     * unchanged publishability rules.
     */
    private boolean meetsClassifiedCoverageFloor(
            com.minhnb.finvera_be.market.domain.breadth.BreadthCalculator.Result result) {
        if (result.eligible() <= 0) {
            return false;
        }
        BigDecimal classified = BigDecimal.valueOf(
                (long) result.advancing() + result.declining() + result.unchanged());
        BigDecimal fraction = classified.divide(
                BigDecimal.valueOf(result.eligible()), 6, java.math.RoundingMode.HALF_UP);
        return fraction.compareTo(minBreadthClassifiedFraction) >= 0;
    }

    private static BigDecimal smaEndingAt(List<BigDecimal> values, int exclusiveEnd, int period) {
        return DecimalTimeSeries.sma(values.subList(0, exclusiveEnd), period);
    }

    private static BigDecimal volatilityPercentile(List<BigDecimal> closes) {
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < closes.size(); i++) returns.add(DecimalTimeSeries.simpleReturn(closes.get(i), closes.get(i - 1)));
        List<BigDecimal> volatilities = new ArrayList<>();
        for (int end = 20; end <= returns.size(); end++) {
            volatilities.add(DecimalTimeSeries.populationStandardDeviation(returns.subList(end - 20, end)));
        }
        BigDecimal current = volatilities.getLast();
        return DecimalTimeSeries.percentileMidRank(current, volatilities);
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
