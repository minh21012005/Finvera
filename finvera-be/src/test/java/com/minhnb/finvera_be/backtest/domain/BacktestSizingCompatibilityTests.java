package com.minhnb.finvera_be.backtest.domain;
import static org.assertj.core.api.Assertions.assertThat;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.*;import java.math.BigDecimal;import java.util.List;import org.junit.jupiter.api.Test;
class BacktestSizingCompatibilityTests{
 @Test void delegatesToPositionSizingAndUsesStandardLot(){var a=new Assumptions(bd("1000000"),bd("0.02"),bd("0.06"),new Costs(bd("0.001"),bd("0.001"),bd("0.001"),bd("0.001"),bd("0.001"),false));var r=BacktestSizingPolicyV1.size(bd("1000000"),bd("1000000"),bd("100"),bd("90"),a,100,List.of());assertThat(r.accepted()).isTrue();assertThat(r.sizing().quantity()%100).isZero();assertThat(r.sizing().estimatedLossAtStopVnd()).isLessThanOrEqualTo(bd("20000"));}
 @Test void rejectsWhenAggregateRiskIsConsumed(){var a=new Assumptions(bd("1000000"),bd("0.02"),bd("0.02"),new Costs(bd("0"),bd("0"),bd("0"),bd("0"),bd("0"),true));var r=BacktestSizingPolicyV1.size(bd("1000000"),bd("1000000"),bd("100"),bd("90"),a,100,List.of(new BacktestSizingPolicyV1.OpenRisk(2000,bd("100"),bd("90"))));assertThat(r.reasonCode()).isEqualTo("AGGREGATE_RISK_EXHAUSTED");}
 private static BigDecimal bd(String x){return new BigDecimal(x);}
}
