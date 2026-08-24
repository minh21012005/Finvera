package com.minhnb.finvera_be.market.domain.regime;

import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.DELAYED;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.PARTIAL;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.UNAVAILABLE;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.FactorDirection;
import com.minhnb.finvera_be.market.domain.regime.MarketRegimeV1.Component;
import com.minhnb.finvera_be.market.domain.regime.MarketRegimeV1.ComponentScore;
import com.minhnb.finvera_be.market.domain.regime.RegimeAssessment.SupportingFactor;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Private live-market regime rule using only provider-proven inputs available today. */
public final class MarketRegimeV2 {
    public static final String RULE_VERSION = "market-regime-v2";

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final MathContext CONTEXT = MathContext.DECIMAL128;
    private static final int SCALE = 6;

    private final MarketRegimeV1 labels = new MarketRegimeV1();
    private static final Map<Component, BigDecimal> WEIGHTS = Map.of(
            Component.TREND, decimal("0.40"),
            Component.BREADTH, decimal("0.25"),
            Component.MOMENTUM, decimal("0.20"),
            Component.VOLATILITY, decimal("0.15"));

    public BigDecimal aggregateBreadthScore(int advancing, int declining) {
        if (advancing < 0 || declining < 0) {
            throw new IllegalArgumentException("breadth counts must be non-negative");
        }
        int denominator = advancing + declining;
        if (denominator == 0) {
            throw new IllegalArgumentException("advancing plus declining must be positive");
        }
        return scale(HUNDRED.multiply(BigDecimal.valueOf(advancing)).divide(BigDecimal.valueOf(denominator), CONTEXT));
    }

    public RegimeAssessment assess(List<ComponentScore> componentScores, InputAvailability availability) {
        Objects.requireNonNull(componentScores, "componentScores");
        Objects.requireNonNull(availability, "availability");
        Map<Component, ComponentScore> usable = uniqueScores(componentScores);
        List<String> reasons = new ArrayList<>();
        DataStatus inputStatus = DataStatus.mostActionable(availability.indexStatus(), availability.breadthStatus());
        BigDecimal completeness = completeness(usable);
        boolean inputsTimely = availability.indexStatus().ordinal() <= DELAYED.ordinal()
                && availability.breadthStatus().ordinal() <= DELAYED.ordinal();
        boolean hasMandatory = usable.containsKey(Component.TREND) && usable.containsKey(Component.BREADTH);
        boolean publishable = availability.indexAvailable() && availability.breadthAvailable()
                && inputsTimely && hasMandatory && usable.size() >= 3;
        if (!publishable) {
            if (!availability.indexAvailable() || !availability.breadthAvailable()) reasons.add("MANDATORY_INPUT_UNAVAILABLE");
            if (!inputsTimely) reasons.add("REQUIRED_INPUT_NOT_TIMELY_AVAILABLE");
            if (!usable.containsKey(Component.TREND)) reasons.add("TREND_COMPONENT_UNAVAILABLE");
            if (!usable.containsKey(Component.BREADTH)) reasons.add("AGGREGATE_BREADTH_COMPONENT_UNAVAILABLE");
            if (usable.size() < 3) reasons.add("INSUFFICIENT_COMPONENT_COMPLETENESS");
            DataStatus status = !availability.indexAvailable() || !availability.breadthAvailable() ? UNAVAILABLE : PARTIAL;
            return new RegimeAssessment(status, null, null, null, completeness, null, null, false, reasons, List.of());
        }

        boolean renormalized = usable.size() != WEIGHTS.size();
        for (Component component : WEIGHTS.keySet()) {
            if (!usable.containsKey(component)) reasons.add("RENORMALIZED_MISSING_" + component.name());
        }
        BigDecimal totalWeight = usable.keySet().stream().map(WEIGHTS::get).reduce(ZERO, BigDecimal::add);
        BigDecimal unroundedScore = ZERO;
        Map<FactorDirection, BigDecimal> agreementWeights = new EnumMap<>(FactorDirection.class);
        for (FactorDirection direction : FactorDirection.values()) agreementWeights.put(direction, ZERO);
        List<SupportingFactor> factors = new ArrayList<>();
        for (ComponentScore componentScore : usable.values()) {
            BigDecimal originalWeight = WEIGHTS.get(componentScore.component());
            BigDecimal effectiveWeight = originalWeight.divide(totalWeight, CONTEXT);
            BigDecimal contribution = componentScore.normalizedScore().multiply(effectiveWeight, CONTEXT);
            unroundedScore = unroundedScore.add(contribution);
            FactorDirection direction = directionFor(componentScore.normalizedScore());
            agreementWeights.compute(direction, (ignored, amount) -> amount.add(effectiveWeight));
            factors.add(new SupportingFactor(componentScore.component(), direction, scale(componentScore.normalizedScore()),
                    originalWeight, scale(effectiveWeight), scale(contribution)));
        }
        int score = unroundedScore.setScale(0, RoundingMode.HALF_UP).intValueExact();
        BigDecimal factorAgreement = scale(agreementWeights.values().stream().max(Comparator.naturalOrder())
                .orElseThrow().multiply(HUNDRED));
        BigDecimal boundaryDistance = boundaryDistance(unroundedScore);
        int confidence = completeness.multiply(decimal("0.45"), CONTEXT)
                .add(factorAgreement.multiply(decimal("0.35"), CONTEXT))
                .add(boundaryDistance.multiply(decimal("0.20"), CONTEXT))
                .setScale(0, RoundingMode.HALF_UP).intValueExact();
        return new RegimeAssessment(inputStatus, labels.labelFor(BigDecimal.valueOf(score)), score, confidence,
                completeness, factorAgreement, boundaryDistance, renormalized, reasons, factors);
    }

    private static Map<Component, ComponentScore> uniqueScores(List<ComponentScore> componentScores) {
        Map<Component, ComponentScore> result = new EnumMap<>(Component.class);
        for (ComponentScore score : componentScores) {
            Objects.requireNonNull(score, "componentScore");
            if (!WEIGHTS.containsKey(score.component())) {
                throw new IllegalArgumentException("component is not part of market-regime-v2");
            }
            if (result.putIfAbsent(score.component(), score) != null) {
                throw new IllegalArgumentException("component score may only occur once");
            }
        }
        return result;
    }

    private static BigDecimal completeness(Map<Component, ComponentScore> usable) {
        return scale(usable.keySet().stream().map(WEIGHTS::get).reduce(ZERO, BigDecimal::add).multiply(HUNDRED));
    }

    private static FactorDirection directionFor(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(55)) > 0) return FactorDirection.POSITIVE;
        if (score.compareTo(BigDecimal.valueOf(45)) < 0) return FactorDirection.NEGATIVE;
        return FactorDirection.NEUTRAL;
    }

    private static BigDecimal boundaryDistance(BigDecimal unroundedScore) {
        BigDecimal boundary;
        BigDecimal maximum;
        if (unroundedScore.compareTo(decimal("29.5")) <= 0) {
            boundary = decimal("29.5"); maximum = decimal("29.5");
        } else if (unroundedScore.compareTo(decimal("44.5")) <= 0) {
            boundary = nearest(unroundedScore, decimal("29.5"), decimal("44.5")); maximum = decimal("7.5");
        } else if (unroundedScore.compareTo(decimal("55.5")) <= 0) {
            boundary = nearest(unroundedScore, decimal("44.5"), decimal("55.5")); maximum = decimal("5.5");
        } else if (unroundedScore.compareTo(decimal("70.5")) <= 0) {
            boundary = nearest(unroundedScore, decimal("55.5"), decimal("70.5")); maximum = decimal("7.5");
        } else {
            boundary = decimal("70.5"); maximum = decimal("29.5");
        }
        return scale(unroundedScore.subtract(boundary).abs().multiply(HUNDRED).divide(maximum, CONTEXT)
                .max(ZERO).min(HUNDRED));
    }

    private static BigDecimal nearest(BigDecimal score, BigDecimal lower, BigDecimal upper) {
        return score.subtract(lower).abs().compareTo(score.subtract(upper).abs()) <= 0 ? lower : upper;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    public record InputAvailability(boolean indexAvailable, boolean breadthAvailable,
                                    DataStatus indexStatus, DataStatus breadthStatus) {
        public InputAvailability {
            Objects.requireNonNull(indexStatus, "indexStatus");
            Objects.requireNonNull(breadthStatus, "breadthStatus");
        }
    }
}
