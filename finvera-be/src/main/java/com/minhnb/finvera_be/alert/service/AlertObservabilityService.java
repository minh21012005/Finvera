package com.minhnb.finvera_be.alert.service;

import com.minhnb.finvera_be.alert.domain.AlertDomain.Decision;
import com.minhnb.finvera_be.alert.repository.AlertDefinitionRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AlertObservabilityService {
    private final MeterRegistry meters;

    @Autowired
    public AlertObservabilityService(MeterRegistry meters,AlertDefinitionRepository definitions,Clock clock) {
        this.meters=meters;
        Gauge.builder("finvera.alert.due.count",()->definitions.countDue(clock.instant())).register(meters);
        Gauge.builder("finvera.alert.oldest.due.age.seconds",()->{
            Instant due=definitions.oldestDueAt(clock.instant());
            return due==null?0:Math.max(0,Duration.between(due,clock.instant()).toSeconds());
        }).register(meters);
    }

    public void evaluated(String type,Decision decision,long nanos) {
        String reason=decision.reasonCode()==null?"NONE":decision.reasonCode();
        meters.counter("finvera.alert.evaluations","type",safe(type),"outcome",decision.outcome().name(),"reason",safe(reason)).increment();
        meters.timer("finvera.alert.evaluation.duration","type",safe(type)).record(nanos,TimeUnit.NANOSECONDS);
    }

    public void delivered(String type){meters.counter("finvera.alert.triggers","type",safe(type)).increment();}
    public void retry(boolean terminal){meters.counter("finvera.alert.failures","terminal",Boolean.toString(terminal)).increment();}
    private static String safe(String value){return value!=null&&value.matches("[A-Z0-9_]{1,64}")?value:"UNKNOWN";}
}
