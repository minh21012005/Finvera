package com.minhnb.finvera_be.backtest.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Assumptions;
import com.minhnb.finvera_be.backtest.domain.BacktestTypes.Costs;
import com.minhnb.finvera_be.backtest.entity.BacktestRunEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BacktestTypesTests {
    @Test void declaredAllZeroCostsCannotBypassExplicitExclusion() {
        assertThatThrownBy(() -> costs(false,"0","0","0","0","0"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("INVALID_COST_POLICY");
    }

    @Test void aggregateRiskCannotBeBelowTrancheRisk() {
        assertThatThrownBy(() -> new Assumptions(new BigDecimal("100000000"),
                new BigDecimal("0.02"),new BigDecimal("0.01"),costs(false,"0.001","0.001","0.001","0.001","0.001")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("AGGREGATE_RISK_BELOW_TRANCHE_RISK");
    }

    @Test void terminalRunCannotBeClaimedOrRequeued() {
        var run=run(); var now=Instant.parse("2026-09-13T00:00:00Z");
        run.claim(now); run.complete("a".repeat(64),10,now.plusSeconds(1));
        assertThatThrownBy(() -> run.claim(now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(run::requeue).isInstanceOf(IllegalStateException.class);
    }

    @Test void recoveryIsBoundedToTwoClaims() {
        var run=run(); var now=Instant.parse("2026-09-13T00:00:00Z");
        run.claim(now); run.requeue(); run.claim(now.plusSeconds(1)); run.requeue();
        assertThatThrownBy(() -> run.claim(now.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class).hasMessage("ATTEMPTS_EXHAUSTED");
    }

    private static BacktestRunEntity run(){
        return new BacktestRunEntity(UUID.randomUUID(),UUID.randomUUID(),null,"MA_CROSSOVER","FPT",
                UUID.randomUUID(),"HOSE",LocalDate.of(2025,1,1),LocalDate.of(2026,1,1),
                new Assumptions(new BigDecimal("100000000"),new BigDecimal("0.005"),new BigDecimal("0.02"),
                        costs(false,"0.001","0.001","0.001","0.001","0.001")),
                Instant.parse("2026-09-13T00:00:00Z"),Instant.parse("2026-09-13T00:00:00Z"));
    }
    private static Costs costs(boolean excluded,String a,String b,String c,String d,String e){
        return new Costs(new BigDecimal(a),new BigDecimal(b),new BigDecimal(c),new BigDecimal(d),new BigDecimal(e),excluded);
    }
}
