package com.minhnb.finvera_be.positioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhnb.finvera_be.positioning.domain.PositionSizingV1;
import com.minhnb.finvera_be.positioning.domain.PositionSizingV1.ConstraintCode;
import java.math.BigDecimal;
import java.util.Random;
import java.util.ArrayList;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PositionSizingV1Tests {
    private static BigDecimal d(String value) { return new BigDecimal(value); }

    @Test
    void checkedInFixtureCatalogCoversEveryRequiredBoundary() throws Exception {
        try (var stream = getClass().getResourceAsStream("/positioning/position-sizing-v1-fixtures.json")) {
            var root = new ObjectMapper().readTree(stream);
            assertThat(root.path("ruleVersion").asText()).isEqualTo(PositionSizingV1.RULE_VERSION);
            assertThat(root.path("lotRuleVersion").asText()).isEqualTo(PositionSizingV1.LOT_RULE_VERSION);
            var names = new ArrayList<String>();
            root.path("fixtures").forEach(node -> names.add(node.path("name").asText()));
            assertThat(names).containsExactly("normal_risk_binding", "risk_and_cash_tie_exact_lot",
                    "below_one_standard_lot", "symbol_cap_with_declared_costs",
                    "money_scale_overflow_rejected", "quantity_long_overflow_rejected");
        }
    }

    @Test
    void computesCostsAppliesAllCapsAndFloorsToStandardLot() {
        var input = new PositionSizingV1.Input(
                d("500000000"), d("300000000"), d("50000"), d("47000"), d("10000000"),
                new PositionSizingV1.Costs(d("0.0015"), d("0.0015"), d("0.001"), d("0.002"), d("0.002")),
                d("500000000"), d("50000000"), d("250000000"), d("0.20"), d("0.70"), 100);

        var result = PositionSizingV1.calculate(input);

        assertThat(result.calculated()).isTrue();
        assertThat(result.quantity()).isEqualTo(900L);
        assertThat(result.rawPermittedQuantity()).isEqualTo(998L);
        assertThat(result.roundingRemainder()).isEqualTo(98L);
        assertThat(result.constraints()).filteredOn(c -> c.code() == ConstraintCode.SYMBOL_CONCENTRATION)
                .singleElement().satisfies(c -> assertThat(c.binding()).isTrue());
        assertThat(result.requiredCapitalVnd()).isEqualByComparingTo("45157635");
        assertThat(result.estimatedLossAtStopVnd()).isPositive().isLessThanOrEqualTo(input.riskBudgetVnd());
    }

    @Test
    void reportsEveryTiedBindingConstraint() {
        var result = PositionSizingV1.calculate(new PositionSizingV1.Input(
                d("1000000"), d("1000000"), d("1000"), d("900"), d("100000"),
                PositionSizingV1.Costs.excluded(), null, null, null, null, null, 100));
        assertThat(result.quantity()).isEqualTo(1000L);
        assertThat(result.constraints()).filteredOn(PositionSizingV1.Constraint::binding)
                .extracting(PositionSizingV1.Constraint::code)
                .containsExactlyInAnyOrder(ConstraintCode.RISK_BUDGET, ConstraintCode.AFFORDABILITY);
    }

    @Test
    void withholdsSubLotAndKeepsAuditValues() {
        var result = PositionSizingV1.calculate(new PositionSizingV1.Input(
                d("1000000"), d("99000"), d("1000"), d("900"), d("9900"),
                PositionSizingV1.Costs.excluded(), null, null, null, null, null, 100));
        assertThat(result.calculated()).isFalse();
        assertThat(result.quantity()).isNull();
        assertThat(result.rawPermittedQuantity()).isEqualTo(99L);
        assertThat(result.reasonCodes()).containsExactly("BELOW_STANDARD_LOT");
    }

    @Test
    void rejectsInvalidLongTradeAndIncompleteExposureBasis() {
        assertThatThrownBy(() -> PositionSizingV1.calculate(new PositionSizingV1.Input(
                d("1000000"), d("1000000"), d("900"), d("900"), d("1000"),
                PositionSizingV1.Costs.excluded(), null, null, null, null, null, 100)))
                .isInstanceOf(PositionSizingV1.ValidationException.class)
                .hasMessage("STOP_NOT_BELOW_ENTRY");
        assertThatThrownBy(() -> new PositionSizingV1.Input(
                d("1000000"), d("1000000"), d("1000"), d("900"), d("1000"),
                PositionSizingV1.Costs.excluded(), null, null, null, d("0.2"), null, 100))
                .isInstanceOf(PositionSizingV1.ValidationException.class);
    }

    @Test
    void rejectsExcessMoneyScaleAndQuantityOverflow() {
        assertThatThrownBy(() -> PositionSizingV1.calculate(new PositionSizingV1.Input(
                d("1000000"), d("1000000"), d("1000.1234567"), d("900"), d("10000"),
                PositionSizingV1.Costs.excluded(), null, null, null, null, null, 100)))
                .isInstanceOf(PositionSizingV1.ValidationException.class)
                .hasMessage("INVALID_ENTRYPRICEVND");
        assertThatThrownBy(() -> PositionSizingV1.calculate(new PositionSizingV1.Input(
                d("99999999999999.999999"), d("99999999999999.999999"), d("0.000002"), d("0.000001"),
                d("99999999999999.999999"), PositionSizingV1.Costs.excluded(),
                null, null, null, null, null, 100)))
                .isInstanceOf(PositionSizingV1.ValidationException.class)
                .hasMessage("QUANTITY_OUT_OF_RANGE");
    }

    @Test
    void randomizedResultsNeverExceedRiskCashOrRoundUp() {
        Random random = new Random(30030L);
        for (int i = 0; i < 500; i++) {
            BigDecimal entry = BigDecimal.valueOf(1_000 + random.nextInt(100_000));
            BigDecimal stop = entry.subtract(BigDecimal.valueOf(1 + random.nextInt(entry.intValue() - 1)));
            BigDecimal cash = BigDecimal.valueOf(100_000L + random.nextInt(10_000_000));
            BigDecimal risk = BigDecimal.valueOf(1_000L + random.nextInt(1_000_000));
            var result = PositionSizingV1.calculate(new PositionSizingV1.Input(
                    cash.max(BigDecimal.ONE), cash, entry, stop, risk,
                    PositionSizingV1.Costs.excluded(), null, null, null, null, null, 100));
            long quantity = result.quantity() == null ? 0 : result.quantity();
            assertThat(quantity % 100).isZero();
            assertThat(result.acquisitionUnitCostVnd().multiply(BigDecimal.valueOf(quantity))).isLessThanOrEqualTo(cash);
            assertThat(result.lossPerShareVnd().multiply(BigDecimal.valueOf(quantity))).isLessThanOrEqualTo(risk);
            assertThat(quantity).isLessThanOrEqualTo(result.rawPermittedQuantity());
        }
    }
}
