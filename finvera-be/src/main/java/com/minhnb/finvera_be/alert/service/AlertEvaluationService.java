package com.minhnb.finvera_be.alert.service;

import com.minhnb.finvera_be.alert.domain.AlertDomain.Decision;
import com.minhnb.finvera_be.alert.domain.AlertDomain.Outcome;
import com.minhnb.finvera_be.alert.entity.*;
import com.minhnb.finvera_be.alert.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertEvaluationService {
    private final AlertDefinitionRepository definitions;
    private final AlertEvaluationRepository evaluations;
    private final AlertNotificationRepository notifications;
    private final AlertDeliveryAttemptRepository deliveries;
    private final AlertFactResolver facts;
    private final AlertObservabilityService telemetry;
    private final Clock clock;

    public AlertEvaluationService(AlertDefinitionRepository definitions, AlertEvaluationRepository evaluations,
            AlertNotificationRepository notifications, AlertDeliveryAttemptRepository deliveries,
            AlertFactResolver facts, AlertObservabilityService telemetry, Clock clock) {
        this.definitions=definitions; this.evaluations=evaluations; this.notifications=notifications;
        this.deliveries=deliveries; this.facts=facts; this.telemetry=telemetry; this.clock=clock;
    }

    @Transactional
    public void evaluate(UUID alertId,UUID leaseToken,Duration interval) {
        var alert=definitions.findLockedById(alertId).orElseThrow();
        if(!alert.isEnabled() || alert.getDeletedAt()!=null || !Objects.equals(alert.getLeaseToken(),leaseToken)) return;
        long started=System.nanoTime();
        short attempt=(short)Math.max(1,alert.getAttemptCount());
        Decision decision=facts.resolve(alert);
        telemetry.evaluated(alert.getConditionType(),decision,System.nanoTime()-started);
        Instant now=clock.instant();
        if(evaluations.existsByAlertIdAndFactKey(alert.getId(),decision.factKey())) {
            alert.complete(decision.factKey(),decision.factAt(),decision.outcome().name(),decision.reasonCode(),now,now.plus(interval));
            return;
        }
        var evaluation=evaluations.save(new AlertEvaluationEntity(UUID.randomUUID(),alert.getId(),alert.getOwnerId(),
                decision.factKey(),decision.factAt(),decision.acceptedAt(),now,decision.outcome().name(),
                decision.reasonCode(),decision.evidence(),attempt,
                (int)Math.min(Integer.MAX_VALUE,(System.nanoTime()-started)/1_000_000)));
        if(decision.outcome()==Outcome.FALSE && !"NEW_DOCUMENT".equals(alert.getConditionType())) alert.falseState();
        if(decision.outcome()==Outcome.TRUE) {
            boolean event=decision.eventKey()!=null;
            if(event || !"TRUE".equals(alert.getEpisodeState())) {
                Long sequence=event?null:alert.trigger(now);
                var notification=notifications.save(new AlertNotificationEntity(UUID.randomUUID(),alert.getOwnerId(),
                        alert.getId(),evaluation.getId(),sequence,event?decision.eventKey():null,
                        "Cảnh báo: "+alert.getName(),alert.getConditionSummary(),alert.getConditionPayload().deepCopy(),
                        decision.evidence().deepCopy(),now));
                deliveries.save(new AlertDeliveryAttemptEntity(UUID.randomUUID(),notification.getId(),now));
                telemetry.delivered(alert.getConditionType());
            }
        }
        alert.complete(decision.factKey(),decision.factAt(),decision.outcome().name(),decision.reasonCode(),now,now.plus(interval));
    }

    @Transactional
    public void fail(UUID alertId,UUID leaseToken,int maxAttempts,Duration retryDelay) {
        var alert=definitions.findLockedById(alertId).orElse(null);
        if(alert==null || !alert.isEnabled() || !Objects.equals(alert.getLeaseToken(),leaseToken)) return;
        Instant now=clock.instant();
        if(alert.getAttemptCount()<maxAttempts) {
            telemetry.retry(false);
            alert.releaseForRetry(now.plus(retryDelay));
            return;
        }
        telemetry.retry(true);
        String key="failure:"+now.toEpochMilli();
        evaluations.save(new AlertEvaluationEntity(UUID.randomUUID(),alert.getId(),alert.getOwnerId(),key,now,now,now,
                "FAILED","EVALUATION_ERROR",tools.jackson.databind.node.JsonNodeFactory.instance.objectNode(),
                alert.getAttemptCount(),0));
        alert.complete(key,now,"FAILED","EVALUATION_ERROR",now,now.plus(retryDelay));
    }
}
