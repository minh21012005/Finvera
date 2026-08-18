package com.minhnb.finvera_be.stock.domain.valuation;

import com.minhnb.finvera_be.stock.domain.model.DecimalMath;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.MetricApplicability;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.ValuationLabel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Normative implementation of contracts/valuation-v1.md.
 * Pure domain engine calculating relative expensiveness classification, score,
 * metric percentiles, and confidence.
 */
public final class ValuationV1 {

    public static final String RULE_VERSION = "valuation-v1";
    public static final String DISCLAIMER_CODE = "QUANTITATIVE_DECISION_SUPPORT";

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal BAND_UNDER_CUTOFF = new BigDecimal("35.5");
    private static final BigDecimal BAND_OVER_CUTOFF = new BigDecimal("64.5");

    private static final int MIN_HISTORY_POINTS = 500;
    private static final int MAX_HISTORY_POINTS = 750;
    private static final int MIN_SECTOR_CONSTITUENTS = 8;

    private static final Map<String, BigDecimal> BASE_WEIGHTS = Map.of(
            "PE", new BigDecimal("0.40"),
            "PB", new BigDecimal("0.30"),
            "EV_EBITDA", new BigDecimal("0.20"),
            "PEG", new BigDecimal("0.10")
    );

    public AssessmentResult classify(Inputs inputs) {
        Objects.requireNonNull(inputs, "inputs");

        List<String> reasonCodes = new ArrayList<>();

        // Check data status
        if ("STALE".equalsIgnoreCase(inputs.priceDataStatus())) {
            reasonCodes.add("PRICE_STALE");
        } else if ("UNAVAILABLE".equalsIgnoreCase(inputs.priceDataStatus())) {
            reasonCodes.add("PRICE_UNAVAILABLE");
        }

        if ("STALE".equalsIgnoreCase(inputs.fundamentalsDataStatus())) {
            reasonCodes.add("FUNDAMENTALS_STALE");
        } else if ("UNAVAILABLE".equalsIgnoreCase(inputs.fundamentalsDataStatus())) {
            reasonCodes.add("FUNDAMENTALS_UNAVAILABLE");
        }

        if (inputs.sourceConflict()) {
            reasonCodes.add("SOURCE_CONFLICT");
        }

        // Compute metrics
        ComputedMetrics computed = computeMetrics(inputs);

        // Filter and evaluate comparison bases
        Map<String, List<BigDecimal>> historyByMetric = groupHistoryByMetric(inputs.ownHistorySeries());
        Map<String, List<BigDecimal>> sectorByMetric = groupSectorByMetric(inputs.sectorSeries());

        boolean basisAOverallAvailable = false;
        boolean basisBOverallAvailable = false;
        int maxHistoryCount = 0;
        int maxSectorCount = 0;

        for (String code : BASE_WEIGHTS.keySet()) {
            int hCount = historyByMetric.getOrDefault(code, List.of()).size();
            if (hCount > maxHistoryCount) maxHistoryCount = hCount;
            if (hCount >= MIN_HISTORY_POINTS) {
                basisAOverallAvailable = true;
            }

            int sCount = sectorByMetric.getOrDefault(code, List.of()).size();
            if (sCount > maxSectorCount) maxSectorCount = sCount;
            if (sCount >= MIN_SECTOR_CONSTITUENTS) {
                basisBOverallAvailable = true;
            }
        }

        if (!basisAOverallAvailable && maxHistoryCount > 0 && maxHistoryCount < MIN_HISTORY_POINTS) {
            reasonCodes.add("HISTORY_BASIS_INSUFFICIENT");
        }
        if (!basisBOverallAvailable && maxSectorCount > 0 && maxSectorCount < MIN_SECTOR_CONSTITUENTS) {
            reasonCodes.add("SECTOR_BASIS_INSUFFICIENT");
        }

        if (!basisAOverallAvailable && !basisBOverallAvailable) {
            reasonCodes.add("NO_COMPARISON_BASIS");
        }

        // Check core metrics (at least one of PE or PB must be DEFINED)
        boolean peDefined = computed.pe().applicability() == MetricApplicability.DEFINED;
        boolean pbDefined = computed.pb().applicability() == MetricApplicability.DEFINED;
        if (!peDefined && !pbDefined) {
            reasonCodes.add("CORE_METRIC_UNAVAILABLE");
        }

        // Compute percentiles for each metric in each basis
        Map<String, BigDecimal> ownPercentiles = new HashMap<>();
        Map<String, BigDecimal> sectorPercentiles = new HashMap<>();

        for (MetricValue mv : computed.allScored()) {
            if (mv.applicability() == MetricApplicability.DEFINED && mv.value() != null) {
                List<BigDecimal> hSeries = historyByMetric.getOrDefault(mv.metricCode(), List.of());
                if (hSeries.size() >= MIN_HISTORY_POINTS) {
                    ownPercentiles.put(mv.metricCode(), percentileRank(mv.value(), hSeries));
                }
                List<BigDecimal> sSeries = sectorByMetric.getOrDefault(mv.metricCode(), List.of());
                if (sSeries.size() >= MIN_SECTOR_CONSTITUENTS) {
                    sectorPercentiles.put(mv.metricCode(), percentileRank(mv.value(), sSeries));
                }
            }
        }

        // Calculate qualifying metric weight for publishability gate
        BigDecimal qualifyingWeight = BigDecimal.ZERO;
        for (MetricValue mv : computed.allScored()) {
            if (mv.applicability() == MetricApplicability.DEFINED &&
                    (ownPercentiles.containsKey(mv.metricCode()) || sectorPercentiles.containsKey(mv.metricCode()))) {
                qualifyingWeight = qualifyingWeight.add(BASE_WEIGHTS.get(mv.metricCode()));
            }
        }

        if (qualifyingWeight.compareTo(new BigDecimal("0.50")) < 0) {
            reasonCodes.add("INSUFFICIENT_METRIC_COVERAGE");
        }

        // Blocking reasons per contracts/valuation-v1.md publishability table:
        boolean isWithheld = reasonCodes.contains("PRICE_STALE")
                || reasonCodes.contains("PRICE_UNAVAILABLE")
                || reasonCodes.contains("FUNDAMENTALS_STALE")
                || reasonCodes.contains("FUNDAMENTALS_UNAVAILABLE")
                || reasonCodes.contains("SOURCE_CONFLICT")
                || reasonCodes.contains("NO_COMPARISON_BASIS")
                || reasonCodes.contains("CORE_METRIC_UNAVAILABLE")
                || reasonCodes.contains("INSUFFICIENT_METRIC_COVERAGE");

        if (isWithheld) {
            // Build metrics without effective weights/scores
            List<MetricResult> metricResults = buildMetricResults(computed, ownPercentiles, sectorPercentiles, Map.of());
            return new AssessmentResult(
                    RULE_VERSION,
                    false,
                    null,
                    null,
                    null,
                    null,
                    basisAOverallAvailable,
                    basisBOverallAvailable,
                    maxHistoryCount,
                    maxSectorCount,
                    metricResults,
                    reasonCodes,
                    DISCLAIMER_CODE,
                    List.of()
            );
        }

        // Calculate effective weights for qualifying metrics per basis and basis scores
        BigDecimal basisAScore = null;
        BigDecimal basisBScore = null;
        Map<String, BigDecimal> effectiveWeights = new HashMap<>();

        if (basisAOverallAvailable) {
            BigDecimal sumWeightsA = BigDecimal.ZERO;
            for (String code : BASE_WEIGHTS.keySet()) {
                if (ownPercentiles.containsKey(code)) {
                    sumWeightsA = sumWeightsA.add(BASE_WEIGHTS.get(code));
                }
            }
            if (sumWeightsA.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal scoreSum = BigDecimal.ZERO;
                for (String code : BASE_WEIGHTS.keySet()) {
                    if (ownPercentiles.containsKey(code)) {
                        BigDecimal effW = DecimalMath.divide12(BASE_WEIGHTS.get(code), sumWeightsA);
                        effectiveWeights.put(code, effW);
                        scoreSum = scoreSum.add(effW.multiply(ownPercentiles.get(code)));
                    }
                }
                basisAScore = scoreSum;
            }
        }

        if (basisBOverallAvailable) {
            BigDecimal sumWeightsB = BigDecimal.ZERO;
            for (String code : BASE_WEIGHTS.keySet()) {
                if (sectorPercentiles.containsKey(code)) {
                    sumWeightsB = sumWeightsB.add(BASE_WEIGHTS.get(code));
                }
            }
            if (sumWeightsB.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal scoreSum = BigDecimal.ZERO;
                for (String code : BASE_WEIGHTS.keySet()) {
                    if (sectorPercentiles.containsKey(code)) {
                        BigDecimal effW = DecimalMath.divide12(BASE_WEIGHTS.get(code), sumWeightsB);
                        scoreSum = scoreSum.add(effW.multiply(sectorPercentiles.get(code)));
                    }
                }
                basisBScore = scoreSum;
            }
        }

        // Renormalize basis weights
        BigDecimal finalValuationScore;
        List<String> usedBases = new ArrayList<>();
        if (basisAScore != null && basisBScore != null) {
            // A = 0.60, B = 0.40 (sum = 1.00)
            finalValuationScore = basisAScore.multiply(new BigDecimal("0.60"))
                    .add(basisBScore.multiply(new BigDecimal("0.40")));
            usedBases.add("OWN_HISTORY");
            usedBases.add("SECTOR");
        } else if (basisAScore != null) {
            finalValuationScore = basisAScore;
            usedBases.add("OWN_HISTORY");
        } else if (basisBScore != null) {
            finalValuationScore = basisBScore;
            usedBases.add("SECTOR");
        } else {
            // Should not reach here due to earlier isWithheld check
            finalValuationScore = null;
        }

        BandResult band = classifyBand(finalValuationScore);

        // Confidence calculation:
        // metricCoverage: sum of baseWeight over scored metrics that are DEFINED and have >=1 basis
        double metricCov = qualifyingWeight.doubleValue();
        double basisCov = (basisAScore != null && basisBScore != null ? 2.0 : (basisAScore != null || basisBScore != null ? 1.0 : 0.0)) / 2.0;
        double historyDepth = Math.min((double) maxHistoryCount / MAX_HISTORY_POINTS, 1.0);
        int confidence = (int) Math.round(100.0 * (0.45 * metricCov + 0.35 * basisCov + 0.20 * historyDepth));

        List<MetricResult> metricResults = buildMetricResults(computed, ownPercentiles, sectorPercentiles, effectiveWeights);

        return new AssessmentResult(
                RULE_VERSION,
                true,
                band.label(),
                finalValuationScore,
                band.displayedScore(),
                confidence,
                basisAScore != null,
                basisBScore != null,
                maxHistoryCount,
                maxSectorCount,
                metricResults,
                reasonCodes,
                DISCLAIMER_CODE,
                usedBases
        );
    }

    public static BandResult classifyBand(BigDecimal score) {
        if (score == null) {
            return new BandResult(null, null, null);
        }
        int displayed = score.setScale(0, RoundingMode.HALF_UP).intValue();
        ValuationLabel label;
        if (score.compareTo(BAND_UNDER_CUTOFF) < 0) {
            label = ValuationLabel.UNDER_VALUED;
        } else if (score.compareTo(BAND_OVER_CUTOFF) >= 0) {
            label = ValuationLabel.OVER_VALUED;
        } else {
            label = ValuationLabel.FAIR_VALUED;
        }
        return new BandResult(label, score, displayed);
    }

    private static BigDecimal percentileRank(BigDecimal x, List<BigDecimal> series) {
        if (series.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long strictlyLess = 0;
        long equalCount = 0;
        for (BigDecimal s : series) {
            int cmp = s.compareTo(x);
            if (cmp < 0) {
                strictlyLess++;
            } else if (cmp == 0) {
                equalCount++;
            }
        }
        // percentile = 100 * (strictlyLess + 0.5 * equalCount) / size
        BigDecimal numerator = BigDecimal.valueOf(strictlyLess).add(HALF.multiply(BigDecimal.valueOf(equalCount)));
        BigDecimal sizeDec = BigDecimal.valueOf(series.size());
        return DecimalMath.divide12(numerator.multiply(HUNDRED), sizeDec);
    }

    private ComputedMetrics computeMetrics(Inputs inputs) {
        BigDecimal price = inputs.price();
        Long shares = inputs.sharesOutstanding();

        // marketCap
        BigDecimal marketCap = (price != null && shares != null && shares > 0)
                ? price.multiply(BigDecimal.valueOf(shares))
                : null;

        // bvps = equityAttributableToParent / sharesOutstanding
        BigDecimal bvps = (inputs.equityAttributableToParent() != null && shares != null && shares > 0)
                ? DecimalMath.divide12(inputs.equityAttributableToParent(), BigDecimal.valueOf(shares))
                : null;

        // ev = marketCap + totalDebt - cashAndEquivalents
        BigDecimal ev = (marketCap != null && inputs.totalDebt() != null && inputs.cashAndEquivalents() != null)
                ? marketCap.add(inputs.totalDebt()).subtract(inputs.cashAndEquivalents())
                : null;

        // 1. PE = price / epsTtm (NOT_APPLICABLE if epsTtm <= 0)
        MetricValue pe;
        if (inputs.epsTtm() == null) {
            pe = new MetricValue("PE", null, MetricApplicability.MISSING, "MISSING_EPS");
        } else if (inputs.epsTtm().compareTo(BigDecimal.ZERO) <= 0) {
            pe = new MetricValue("PE", null, MetricApplicability.NOT_APPLICABLE, "NEGATIVE_OR_ZERO_EPS");
        } else if (price == null) {
            pe = new MetricValue("PE", null, MetricApplicability.MISSING, "MISSING_PRICE");
        } else {
            pe = new MetricValue("PE", DecimalMath.divide12(price, inputs.epsTtm()), MetricApplicability.DEFINED, null);
        }

        // 2. PB = price / bvps (NOT_APPLICABLE if bvps <= 0)
        MetricValue pb;
        if (bvps == null) {
            pb = new MetricValue("PB", null, MetricApplicability.MISSING, "MISSING_BVPS");
        } else if (bvps.compareTo(BigDecimal.ZERO) <= 0) {
            pb = new MetricValue("PB", null, MetricApplicability.NOT_APPLICABLE, "NEGATIVE_OR_ZERO_BVPS");
        } else if (price == null) {
            pb = new MetricValue("PB", null, MetricApplicability.MISSING, "MISSING_PRICE");
        } else {
            pb = new MetricValue("PB", DecimalMath.divide12(price, bvps), MetricApplicability.DEFINED, null);
        }

        // 3. EV_EBITDA = ev / ebitdaTtm (NOT_APPLICABLE if ebitdaTtm <= 0 or ev == null)
        MetricValue evEbitda;
        if (inputs.ebitdaTtm() == null) {
            evEbitda = new MetricValue("EV_EBITDA", null, MetricApplicability.MISSING, "MISSING_EBITDA");
        } else if (inputs.ebitdaTtm().compareTo(BigDecimal.ZERO) <= 0) {
            evEbitda = new MetricValue("EV_EBITDA", null, MetricApplicability.NOT_APPLICABLE, "NEGATIVE_OR_ZERO_EBITDA");
        } else if (ev == null) {
            evEbitda = new MetricValue("EV_EBITDA", null, MetricApplicability.MISSING, "MISSING_EV_INPUTS");
        } else {
            evEbitda = new MetricValue("EV_EBITDA", DecimalMath.divide12(ev, inputs.ebitdaTtm()), MetricApplicability.DEFINED, null);
        }

        // 4. PEG = PE / epsGrowthPercent (NOT_APPLICABLE if PE is not DEFINED or epsGrowthPercent <= 0)
        MetricValue peg;
        if (pe.applicability() != MetricApplicability.DEFINED || pe.value() == null) {
            peg = new MetricValue("PEG", null, MetricApplicability.NOT_APPLICABLE, "PE_NOT_DEFINED");
        } else if (inputs.epsGrowthPercent() == null) {
            peg = new MetricValue("PEG", null, MetricApplicability.MISSING, "MISSING_GROWTH");
        } else if (inputs.epsGrowthPercent().compareTo(BigDecimal.ZERO) <= 0) {
            peg = new MetricValue("PEG", null, MetricApplicability.NOT_APPLICABLE, "NEGATIVE_OR_ZERO_GROWTH");
        } else {
            peg = new MetricValue("PEG", DecimalMath.divide12(pe.value(), inputs.epsGrowthPercent()), MetricApplicability.DEFINED, null);
        }

        // 5. DIVIDEND_YIELD = dividendPerShareTtm / price * 100
        MetricValue dividendYield;
        if (inputs.dividendPerShareTtm() == null) {
            dividendYield = new MetricValue("DIVIDEND_YIELD", null, MetricApplicability.MISSING, "MISSING_DIVIDEND");
        } else if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
            dividendYield = new MetricValue("DIVIDEND_YIELD", null, MetricApplicability.NOT_APPLICABLE, "ZERO_PRICE");
        } else {
            BigDecimal yieldVal = DecimalMath.divide12(inputs.dividendPerShareTtm().multiply(HUNDRED), price);
            dividendYield = new MetricValue("DIVIDEND_YIELD", yieldVal, MetricApplicability.DEFINED, null);
        }

        return new ComputedMetrics(pe, pb, evEbitda, peg, dividendYield);
    }

    private List<MetricResult> buildMetricResults(
            ComputedMetrics computed,
            Map<String, BigDecimal> ownPct,
            Map<String, BigDecimal> secPct,
            Map<String, BigDecimal> effWeights) {
        List<MetricResult> list = new ArrayList<>();
        for (MetricValue mv : List.of(computed.pe(), computed.pb(), computed.evEbitda(), computed.peg(), computed.dividendYield())) {
            BigDecimal effW = effWeights.get(mv.metricCode());
            list.add(new MetricResult(
                    mv.metricCode(),
                    mv.value(),
                    mv.applicability(),
                    ownPct.get(mv.metricCode()),
                    secPct.get(mv.metricCode()),
                    effW,
                    mv.qualityReason()
            ));
        }
        return list;
    }

    private Map<String, List<BigDecimal>> groupHistoryByMetric(List<HistoryPoint> series) {
        Map<String, List<BigDecimal>> map = new HashMap<>();
        if (series != null) {
            for (HistoryPoint hp : series) {
                if (hp.value() != null) {
                    map.computeIfAbsent(hp.metricCode(), k -> new ArrayList<>()).add(hp.value());
                }
            }
        }
        return map;
    }

    private Map<String, List<BigDecimal>> groupSectorByMetric(List<SectorPoint> series) {
        Map<String, List<BigDecimal>> map = new HashMap<>();
        if (series != null) {
            for (SectorPoint sp : series) {
                if (sp.value() != null) {
                    map.computeIfAbsent(sp.metricCode(), k -> new ArrayList<>()).add(sp.value());
                }
            }
        }
        return map;
    }

    // ── Records and Builders ───────────────────────────────────────────────────

    public record HistoryPoint(String metricCode, BigDecimal value) {}
    public record SectorPoint(String instrumentId, String metricCode, BigDecimal value) {}
    public record MetricValue(String metricCode, BigDecimal value, MetricApplicability applicability, String qualityReason) {}
    public record ComputedMetrics(MetricValue pe, MetricValue pb, MetricValue evEbitda, MetricValue peg, MetricValue dividendYield) {
        public List<MetricValue> allScored() {
            return List.of(pe, pb, evEbitda, peg);
        }
    }

    public record BandResult(ValuationLabel label, BigDecimal score, Integer displayedScore) {}

    public record MetricResult(
            String metricCode,
            BigDecimal value,
            MetricApplicability applicability,
            BigDecimal ownHistoryPercentile,
            BigDecimal sectorPercentile,
            BigDecimal effectiveWeight,
            String reasonCode
    ) {}

    public record AssessmentResult(
            String ruleVersion,
            boolean published,
            ValuationLabel classification,
            BigDecimal score,
            Integer displayedScore,
            Integer confidence,
            boolean usedOwnHistory,
            boolean usedSector,
            int historyPointCount,
            int sectorConstituentCount,
            List<MetricResult> metrics,
            List<String> reasonCodes,
            String disclaimerCode,
            List<String> usedBases
    ) {}

    public record Inputs(
            BigDecimal price,
            Long sharesOutstanding,
            BigDecimal epsTtm,
            BigDecimal epsGrowthPercent,
            BigDecimal equityAttributableToParent,
            BigDecimal ebitdaTtm,
            BigDecimal totalDebt,
            BigDecimal cashAndEquivalents,
            BigDecimal dividendPerShareTtm,
            List<HistoryPoint> ownHistorySeries,
            List<SectorPoint> sectorSeries,
            String priceDataStatus,
            String fundamentalsDataStatus,
            boolean sourceConflict
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private BigDecimal price;
            private Long sharesOutstanding;
            private BigDecimal epsTtm;
            private BigDecimal epsGrowthPercent;
            private BigDecimal equityAttributableToParent;
            private BigDecimal ebitdaTtm;
            private BigDecimal totalDebt;
            private BigDecimal cashAndEquivalents;
            private BigDecimal dividendPerShareTtm;
            private List<HistoryPoint> ownHistorySeries = List.of();
            private List<SectorPoint> sectorSeries = List.of();
            private String priceDataStatus = "CURRENT";
            private String fundamentalsDataStatus = "CURRENT";
            private boolean sourceConflict = false;

            public Builder price(BigDecimal price) { this.price = price; return this; }
            public Builder sharesOutstanding(Long shares) { this.sharesOutstanding = shares; return this; }
            public Builder epsTtm(BigDecimal eps) { this.epsTtm = eps; return this; }
            public Builder epsGrowthPercent(BigDecimal g) { this.epsGrowthPercent = g; return this; }
            public Builder equityAttributableToParent(BigDecimal eq) { this.equityAttributableToParent = eq; return this; }
            public Builder ebitdaTtm(BigDecimal eb) { this.ebitdaTtm = eb; return this; }
            public Builder totalDebt(BigDecimal d) { this.totalDebt = d; return this; }
            public Builder cashAndEquivalents(BigDecimal c) { this.cashAndEquivalents = c; return this; }
            public Builder dividendPerShareTtm(BigDecimal div) { this.dividendPerShareTtm = div; return this; }
            public Builder ownHistorySeries(List<HistoryPoint> h) { this.ownHistorySeries = h; return this; }
            public Builder sectorSeries(List<SectorPoint> s) { this.sectorSeries = s; return this; }
            public Builder priceDataStatus(String s) { this.priceDataStatus = s; return this; }
            public Builder fundamentalsDataStatus(String s) { this.fundamentalsDataStatus = s; return this; }
            public Builder sourceConflict(boolean c) { this.sourceConflict = c; return this; }

            public Inputs build() {
                return new Inputs(
                        price, sharesOutstanding, epsTtm, epsGrowthPercent,
                        equityAttributableToParent, ebitdaTtm, totalDebt,
                        cashAndEquivalents, dividendPerShareTtm,
                        ownHistorySeries, sectorSeries, priceDataStatus,
                        fundamentalsDataStatus, sourceConflict
                );
            }
        }
    }
}
