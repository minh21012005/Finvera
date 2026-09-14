package com.minhnb.finvera_be.backtest.domain;
import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;import java.util.List;import org.junit.jupiter.api.Test;
class PyramidingV1Tests{
 @Test void requiresANewEpisode(){assertThat(PyramidingV1.evaluate(true,true,List.of(),bd("11"),bd("2")).reasonCode()).isEqualTo("NOT_NEW_SIGNAL_EPISODE");}
 @Test void acceptsExactlyHalfAtrAboveLatestFill(){assertThat(PyramidingV1.evaluate(false,true,List.of(bd("10")),bd("11"),bd("2")).accepted()).isTrue();}
 @Test void capsFourOpenTranches(){assertThat(PyramidingV1.evaluate(false,true,List.of(bd("1"),bd("2"),bd("3"),bd("4")),bd("6"),bd("2")).reasonCode()).isEqualTo("MAX_TRANCHES_REACHED");}
 private static BigDecimal bd(String x){return new BigDecimal(x);}
}
