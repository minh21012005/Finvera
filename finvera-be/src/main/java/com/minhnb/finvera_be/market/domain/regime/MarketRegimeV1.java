package com.minhnb.finvera_be.market.domain.regime;

import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.DELAYED;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.PARTIAL;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.UNAVAILABLE;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.FactorDirection;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.RegimeLabel;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Pure deterministic implementation of the approved {@code market-regime-v1} methodology. */
public final class MarketRegimeV1 {

    public static final String RULE_VERSION = "market-regime-v1";

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal COMPLETENESS_THRESHOLD = BigDecimal.valueOf(80);
    private static final MathContext CONTEXT = MathContext.DECIMAL128;
    private static final int SCALE = 6;

    public enum Component {
        TREND("0.35"), BREADTH("0.25"), MOMENTUM("0.15"), LIQUIDITY("0.15"), VOLATILITY("0.10");

        private final BigDecimal weight;

        Component(String weight) {
            this.weight = new BigDecimal(weight);
        }

        public BigDecimal weight() {
            return weight;
        }
    }

    public record ComponentScore(Component component, BigDecimal normalizedScore) {
        public ComponentScore {
            Objects.requireNonNull(component, "component");
            Objects.requireNonNull(normalizedScore, "normalizedScore");
            if (normalizedScore.compareTo(ZERO) < 0 || normalizedScore.compareTo(HUNDRED) > 0) {
                throw new IllegalArgumentException("normalized score must be within 0..100");
            }
        }
    }

    public record InputAvailability(boolean indexAvailable, boolean breadthAvailable,
                                    DataStatus indexStatus, DataStatus breadthStatus) {
        public InputAvailability {
            Objects.requireNonNull(indexStatus, "indexStatus");
            Objects.requireNonNull(breadthStatus, "breadthStatus");
        }
    }

    public record TrendInput(BigDecimal close, BigDecimal sma20, BigDecimal sma50,
                             BigDecimal sma200, BigDecimal sma20TwentySessionsAgo) {
        public TrendInput {
            Objects.requireNonNull(close, "close");
            Objects.requireNonNull(sma20, "sma20");
            Objects.requireNonNull(sma50, "sma50");
            Objects.requireNonNull(sma200, "sma200");
            Objects.requireNonNull(sma20TwentySessionsAgo, "sma20TwentySessionsAgo");
        }
    }

    public record BreadthInput(int advancing, int declining, BigDecimal percentEligibleAboveSma50) {
        public BreadthInput {
            if (advancing < 0 || declining < 0) {
                throw new IllegalArgumentException("breadth counts must be non-negative");
            }
            Objects.requireNonNull(percentEligibleAboveSma50, "percentEligibleAboveSma50");
        }
    }

    public BigDecimal trendScore(TrendInput input) {
        Objects.requireNonNull(input, "input");
        int passingSignals = 0;
        passingSignals += input.close().compareTo(input.sma20()) > 0 ? 1 : 0;
        passingSignals += input.sma20().compareTo(input.sma50()) > 0 ? 1 : 0;
        passingSignals += input.sma50().compareTo(input.sma200()) > 0 ? 1 : 0;
        passingSignals += input.sma20().compareTo(input.sma20TwentySessionsAgo()) > 0 ? 1 : 0;
        return scale(HUNDRED.multiply(BigDecimal.valueOf(passingSignals)).divide(BigDecimal.valueOf(4), CONTEXT));
    }

    public Optional<BigDecimal> breadthScore(BreadthInput input) {
        Objects.requireNonNull(input, "input");
        int denominator = input.advancing() + input.declining();
        if (denominator == 0) {
            return Optional.empty();
        }
        BigDecimal advanceDeclineScore = HUNDRED.multiply(BigDecimal.valueOf(input.advancing()))
                .divide(BigDecimal.valueOf(denominator), CONTEXT);
        return Optional.of(scale(advanceDeclineScore.add(clamp(input.percentEligibleAboveSma50())).divide(TWO, CONTEXT)));
    }

    public BigDecimal momentumScore(BigDecimal rsi14, BigDecimal twentySessionReturn) {
        Objects.requireNonNull(rsi14, "rsi14");
        Objects.requireNonNull(twentySessionReturn, "twentySessionReturn");
        BigDecimal rsiScore = clamp(rsi14.subtract(BigDecimal.valueOf(30)).multiply(HUNDRED).divide(BigDecimal.valueOf(40), CONTEXT));
        BigDecimal returnScore = clamp(twentySessionReturn.add(decimal("0.10"))
                .multiply(HUNDRED).divide(decimal("0.20"), CONTEXT));
        return scale(rsiScore.add(returnScore).divide(TWO, CONTEXT));
    }

    public BigDecimal liquidityScore(BigDecimal currentToMedianRatio) {
        Objects.requireNonNull(currentToMedianRatio, "currentToMedianRatio");
        return scale(clamp(currentToMedianRatio.subtract(decimal("0.50"))
                .multiply(HUNDRED).divide(ONE, CONTEXT)));
    }

    public BigDecimal volatilityScore(BigDecimal volatilityPercentile) {
        Objects.requireNonNull(volatilityPercentile, "volatilityPercentile");
        return scale(HUNDRED.subtract(clamp(volatilityPercentile)));
    }

    public boolean meetsCompletenessThreshold(BigDecimal completeness) {
        return Objects.requireNonNull(completeness, "completeness").compareTo(COMPLETENESS_THRESHOLD) >= 0;
    }

    public RegimeLabel labelFor(BigDecimal roundedScore) {
        int score = Objects.requireNonNull(roundedScore, "roundedScore").setScale(0, RoundingMode.HALF_UP).intValueExact();
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be within 0..100");
        }
        if (score <= 29) return RegimeLabel.BEAR;
        if (score <= 44) return RegimeLabel.EARLY_BEAR;
        if (score <= 55) return RegimeLabel.SIDEWAYS;
        if (score <= 70) return RegimeLabel.EARLY_BULL;
        return RegimeLabel.BULL;
    }

    public RegimeAssessment assess(List<ComponentScore> componentScores, InputAvailability availability) {
        Objects.requireNonNull(componentScores, "componentScores");
        Objects.requireNonNull(availability, "availability");
        Map<Component, ComponentScore> usable = uniqueScores(componentScores);
        List<String> reasons = new ArrayList<>();
        DataStatus inputStatus = DataStatus.mostActionable(availability.indexStatus(), availability.breadthStatus());
        BigDecimal completeness = completeness(usable.keySet());
        boolean inputsTimely = availability.indexStatus().ordinal() <= DELAYED.ordinal()
                && availability.breadthStatus().ordinal() <= DELAYED.ordinal();
        boolean publishable = availability.indexAvailable() && availability.breadthAvailable()
                && inputsTimely && usable.size() >= 4 && meetsCompletenessThreshold(completeness);
        if (!publishable) {
            if (!availability.indexAvailable() || !availability.breadthAvailable()) reasons.add("MANDATORY_INPUT_UNAVAILABLE");
            if (!inputsTimely) reasons.add("REQUIRED_INPUT_NOT_TIMELY_AVAILABLE");
            if (usable.size() < 4 || !meetsCompletenessThreshold(completeness)) reasons.add("INSUFFICIENT_COMPONENT_COMPLETENESS");
            DataStatus status = !availability.indexAvailable() || !availability.breadthAvailable() ? UNAVAILABLE : PARTIAL;
            return new RegimeAssessment(status, null, null, null, completeness, null, null, false, reasons, List.of());
        }

        boolean renormalized = usable.size() != Component.values().length;
        Component missing = renormalized ? EnumSet.complementOf(EnumSet.copyOf(usable.keySet())).iterator().next() : null;
        if (missing != null) reasons.add("RENORMALIZED_MISSING_" + missing.name());
        BigDecimal totalWeight = usable.keySet().stream().map(Component::weight).reduce(ZERO, BigDecimal::add);
        BigDecimal unroundedScore = ZERO;
        Map<FactorDirection, BigDecimal> agreementWeights = new EnumMap<>(FactorDirection.class);
        List<RegimeAssessment.SupportingFactor> factors = new ArrayList<>();
        for (FactorDirection direction : FactorDirection.values()) agreementWeights.put(direction, ZERO);
        for (ComponentScore componentScore : usable.values()) {
            BigDecimal effectiveWeight = componentScore.component().weight().divide(totalWeight, CONTEXT);
            BigDecimal contribution = componentScore.normalizedScore().multiply(effectiveWeight, CONTEXT);
            unroundedScore = unroundedScore.add(contribution);
            FactorDirection direction = directionFor(componentScore.normalizedScore());
            agreementWeights.compute(direction, (ignored, amount) -> amount.add(effectiveWeight));
            factors.add(new RegimeAssessment.SupportingFactor(componentScore.component(), direction,
                    scale(componentScore.normalizedScore()), componentScore.component().weight(),
                    scale(effectiveWeight), scale(contribution)));
        }
        int score = unroundedScore.setScale(0, RoundingMode.HALF_UP).intValueExact();
        BigDecimal factorAgreement = scale(agreementWeights.values().stream().max(Comparator.naturalOrder()).orElseThrow().multiply(HUNDRED));
        BigDecimal boundaryDistance = boundaryDistance(unroundedScore);
        int confidence = completeness.multiply(decimal("0.50"), CONTEXT)
                .add(factorAgreement.multiply(decimal("0.30"), CONTEXT))
                .add(boundaryDistance.multiply(decimal("0.20"), CONTEXT))
                .setScale(0, RoundingMode.HALF_UP).intValueExact();
        return new RegimeAssessment(inputStatus, labelFor(BigDecimal.valueOf(score)), score, confidence,
                completeness, factorAgreement, boundaryDistance, renormalized, reasons, factors);
    }

    private static Map<Component, ComponentScore> uniqueScores(List<ComponentScore> componentScores) {
        Map<Component, ComponentScore> result = new EnumMap<>(Component.class);
        for (ComponentScore score : componentScores) {
            Objects.requireNonNull(score, "componentScore");
            if (result.putIfAbsent(score.component(), score) != null) {
                throw new IllegalArgumentException("component score may only occur once");
            }
        }
        return result;
    }

    private static BigDecimal completeness(Iterable<Component> components) {
        BigDecimal weight = ZERO;
        for (Component component : components) weight = weight.add(component.weight());
        return scale(weight.multiply(HUNDRED));
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
        return scale(clamp(unroundedScore.subtract(boundary).abs().multiply(HUNDRED).divide(maximum, CONTEXT)));
    }

    private static BigDecimal nearest(BigDecimal score, BigDecimal lower, BigDecimal upper) {
        return score.subtract(lower).abs().compareTo(score.subtract(upper).abs()) <= 0 ? lower : upper;
    }

    private static BigDecimal clamp(BigDecimal value) {
        return value.max(ZERO).min(HUNDRED);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
