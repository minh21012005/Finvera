package com.minhnb.finvera_be.market.domain.regime;

import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.CURRENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.RegimeLabel;
import com.minhnb.finvera_be.market.domain.regime.MarketRegimeV1.Component;
import com.minhnb.finvera_be.market.domain.regime.MarketRegimeV1.ComponentScore;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketRegimeV2Tests {
    private final MarketRegimeV2 regime = new MarketRegimeV2();

    @Test
    void publishesWithTrendAggregateBreadthAndOneAdditionalComponent() {
        var assessment = regime.assess(List.of(
                        component(Component.TREND, "80"),
                        component(Component.BREADTH, "70"),
                        component(Component.MOMENTUM, "50")),
                new MarketRegimeV2.InputAvailability(true, true, CURRENT, CURRENT));

        assertThat(assessment.label()).isEqualTo(RegimeLabel.EARLY_BULL);
        assertThat(assessment.score()).isEqualTo(70);
        assertThat(assessment.confidence()).isNotNull();
        assertThat(assessment.renormalized()).isTrue();
        assertThat(assessment.reasonCodes()).contains("RENORMALIZED_MISSING_VOLATILITY");
        assertThat(assessment.factors()).hasSize(3);
    }

    @Test
    void withholdsWhenMandatoryTrendIsMissingEvenIfThreeComponentsExist() {
        var assessment = regime.assess(List.of(
                        component(Component.BREADTH, "70"),
                        component(Component.MOMENTUM, "60"),
                        component(Component.VOLATILITY, "50")),
                new MarketRegimeV2.InputAvailability(true, true, CURRENT, CURRENT));

        assertThat(assessment.label()).isNull();
        assertThat(assessment.reasonCodes()).contains("TREND_COMPONENT_UNAVAILABLE");
    }

    @Test
    void aggregateBreadthUsesOnlyAdvancingAndDecliningCounts() {
        assertThat(regime.aggregateBreadthScore(3, 1)).isEqualByComparingTo("75.000000");
        assertThatThrownBy(() -> regime.aggregateBreadthScore(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ComponentScore component(Component component, String score) {
        return new ComponentScore(component, new BigDecimal(score));
    }
}
