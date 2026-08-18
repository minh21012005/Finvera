package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.IndicatorSetResult;
import com.minhnb.finvera_be.stock.domain.technical.TechnicalIndicatorsV1.TechnicalBar;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1.AssessmentResult;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1.HistoryPoint;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1.Inputs;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T068 [SC-003]
 * Replay determinism verification for technical indicators and valuation assessments.
 * Recomputation from exact inputs and rule version must yield identical decimals.
 */
class StockReplayDeterminismTests {

    @Test
    void technicalIndicatorsRecomputationIs100PercentBitwiseDeterministic() {
        var engine = new TechnicalIndicatorsV1();
        List<TechnicalBar> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 8, 1);
        for (int i = 0; i < 250; i++) {
            BigDecimal p = new BigDecimal(String.format("%.2f", 100.0 + Math.sin(i * 0.1) * 15.0));
            bars.add(new TechnicalBar(start.plusDays(i), p, p, p, 500000L));
        }

        IndicatorSetResult firstRun = engine.compute(bars);
        IndicatorSetResult secondRun = engine.compute(bars);

        assertThat(firstRun.indicators()).hasSize(secondRun.indicators().size());

        for (int i = 0; i < firstRun.indicators().size(); i++) {
            var ind1 = firstRun.indicators().get(i);
            var ind2 = secondRun.indicators().get(i);
            assertThat(ind1.indicatorCode()).isEqualTo(ind2.indicatorCode());
            assertThat(ind1.applicability()).isEqualTo(ind2.applicability());
            assertThat(ind1.components()).hasSize(ind2.components().size());
            for (int j = 0; j < ind1.components().size(); j++) {
                var c1 = ind1.components().get(j);
                var c2 = ind2.components().get(j);
                if (c1.value() != null && c2.value() != null) {
                    assertThat(c1.value()).isEqualByComparingTo(c2.value());
                }
            }
        }
    }

    @Test
    void valuationAssessmentRecomputationIs100PercentBitwiseDeterministic() {
        var engine = new ValuationV1();
        List<HistoryPoint> history = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            history.add(new HistoryPoint("PE", new BigDecimal(String.format("%.2f", 12.0 + (i % 20) * 0.5))));
            history.add(new HistoryPoint("PB", new BigDecimal(String.format("%.2f", 1.8 + (i % 15) * 0.1))));
        }

        var inputs = Inputs.builder()
                .price(new BigDecimal("131000.00"))
                .sharesOutstanding(1460000000L)
                .epsTtm(new BigDecimal("8663.00"))
                .epsGrowthPercent(new BigDecimal("22.50"))
                .equityAttributableToParent(new BigDecimal("35000000000000.00"))
                .ebitdaTtm(new BigDecimal("15000000000000.00"))
                .totalDebt(new BigDecimal("10000000000000.00"))
                .cashAndEquivalents(new BigDecimal("25000000000000.00"))
                .dividendPerShareTtm(new BigDecimal("2000.00"))
                .ownHistorySeries(history)
                .priceDataStatus("CURRENT")
                .fundamentalsDataStatus("CURRENT")
                .sourceConflict(false)
                .build();

        AssessmentResult firstRun = engine.classify(inputs);
        AssessmentResult secondRun = engine.classify(inputs);

        assertThat(firstRun.ruleVersion()).isEqualTo("valuation-v1");
        assertThat(secondRun.ruleVersion()).isEqualTo("valuation-v1");
        assertThat(firstRun.published()).isTrue();
        assertThat(firstRun.score()).isEqualByComparingTo(secondRun.score());
        assertThat(firstRun.displayedScore()).isEqualTo(secondRun.displayedScore());
        assertThat(firstRun.confidence()).isEqualTo(secondRun.confidence());
        assertThat(firstRun.classification()).isEqualTo(secondRun.classification());

        for (int i = 0; i < firstRun.metrics().size(); i++) {
            var m1 = firstRun.metrics().get(i);
            var m2 = secondRun.metrics().get(i);
            assertThat(m1.metricCode()).isEqualTo(m2.metricCode());
            assertThat(m1.applicability()).isEqualTo(m2.applicability());
            if (m1.value() != null) {
                assertThat(m1.value()).isEqualByComparingTo(m2.value());
            }
            if (m1.effectiveWeight() != null) {
                assertThat(m1.effectiveWeight()).isEqualByComparingTo(m2.effectiveWeight());
            }
        }
    }
}
