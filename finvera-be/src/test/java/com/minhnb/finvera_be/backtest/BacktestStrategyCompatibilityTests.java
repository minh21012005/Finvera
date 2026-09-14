package com.minhnb.finvera_be.backtest;
import static org.assertj.core.api.Assertions.assertThat;
import tools.jackson.databind.ObjectMapper;
import com.minhnb.finvera_be.stock.domain.model.StockTypes.StrategyCode;import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1;import java.io.InputStream;import java.math.BigDecimal;import java.util.*;import org.junit.jupiter.api.Test;
class BacktestStrategyCompatibilityTests{
 @Test void fixtureCoversEveryStrategyAndEvaluatorHandlesPointInTimeInput()throws Exception{try(InputStream in=getClass().getResourceAsStream("/backtest/strategies/all-eight-v1.json")){var root=new ObjectMapper().readTree(in);List<String> names=new ArrayList<>();root.forEach(x->names.add(x.asString()));assertThat(names).containsExactlyInAnyOrder(Arrays.stream(StrategyCode.values()).map(Enum::name).toArray(String[]::new));var input=new StrategySignalV1.StrategyInputs(new BigDecimal("100"),Map.of(),Map.of(),List.of());for(var code:StrategyCode.values())assertThat(StrategySignalV1.evaluate(code,input).status()).isEqualTo(StrategySignalV1.EntryStatus.INSUFFICIENT_HISTORY);}}
}
