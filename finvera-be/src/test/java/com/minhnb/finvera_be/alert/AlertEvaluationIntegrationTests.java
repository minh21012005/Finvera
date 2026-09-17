package com.minhnb.finvera_be.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.minhnb.finvera_be.alert.domain.AlertDomain.*;
import com.minhnb.finvera_be.alert.entity.*;
import com.minhnb.finvera_be.alert.repository.*;
import com.minhnb.finvera_be.alert.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

class AlertEvaluationIntegrationTests {
    private final AlertDefinitionRepository definitions=mock(AlertDefinitionRepository.class);
    private final AlertEvaluationRepository evaluations=mock(AlertEvaluationRepository.class);
    private final AlertNotificationRepository notifications=mock(AlertNotificationRepository.class);
    private final AlertDeliveryAttemptRepository deliveries=mock(AlertDeliveryAttemptRepository.class);
    private final AlertFactResolver facts=mock(AlertFactResolver.class);
    private final AlertObservabilityService telemetry=mock(AlertObservabilityService.class);
    private final Instant now=Instant.parse("2026-09-16T00:00:00Z");
    private final ObjectMapper json=new ObjectMapper();
    private AlertDefinitionEntity alert;
    private AlertEvaluationService service;

    @BeforeEach void setUp(){alert=new AlertDefinitionEntity(UUID.randomUUID(),UUID.randomUUID(),"VCB","PRICE_ABOVE",json.createObjectNode(),"summary",now);when(definitions.findLockedById(alert.getId())).thenReturn(Optional.of(alert));when(evaluations.save(any())).thenAnswer(x->x.getArgument(0));when(notifications.save(any())).thenAnswer(x->x.getArgument(0));service=new AlertEvaluationService(definitions,evaluations,notifications,deliveries,facts,telemetry,Clock.fixed(now,ZoneOffset.UTC));}
    private Decision decision(Outcome outcome,String key){return new Decision(outcome,null,json.createObjectNode(),key,now,now,null);}
    private void evaluate(Decision decision){UUID token=UUID.randomUUID();alert.claim(token,now.plusSeconds(60));when(facts.resolve(alert)).thenReturn(decision);service.evaluate(alert.getId(),token,Duration.ofSeconds(10));}

    @Test void continuousTrueEpisodeDeliversOnce(){evaluate(decision(Outcome.TRUE,"fact-1"));evaluate(decision(Outcome.TRUE,"fact-2"));verify(notifications,times(1)).save(any());assertThat(alert.getEpisodeState()).isEqualTo("TRUE");}

    @Test void falseRearmsAndNewTrueDeliversAgain(){evaluate(decision(Outcome.TRUE,"fact-1"));evaluate(decision(Outcome.FALSE,"fact-2"));evaluate(decision(Outcome.TRUE,"fact-3"));verify(notifications,times(2)).save(any());assertThat(alert.getEpisodeSequence()).isEqualTo(2);}

    @Test void duplicateFactDoesNotCreateSecondEvaluationOrNotification(){when(evaluations.existsByAlertIdAndFactKey(alert.getId(),"same")).thenReturn(true);evaluate(decision(Outcome.TRUE,"same"));verify(evaluations,never()).save(any());verify(notifications,never()).save(any());}

    @Test void emptyDocumentPollDoesNotChangeEpisodeState(){alert=new AlertDefinitionEntity(UUID.randomUUID(),UUID.randomUUID(),"document","NEW_DOCUMENT",json.createObjectNode(),"summary",now);when(definitions.findLockedById(alert.getId())).thenReturn(Optional.of(alert));evaluate(decision(Outcome.FALSE,"document-baseline:"+now));assertThat(alert.getEpisodeState()).isEqualTo("UNKNOWN");verify(notifications,never()).save(any());}

    @Test void expiredWorkerTokenCannotWrite(){UUID current=UUID.randomUUID();alert.claim(current,now.plusSeconds(60));service.evaluate(alert.getId(),UUID.randomUUID(),Duration.ofSeconds(10));verifyNoInteractions(facts);verify(evaluations,never()).save(any());}

    @Test void secondFailureIsTerminalAndDoesNotScheduleAnImmediateThirdAttempt(){UUID first=UUID.randomUUID();alert.claim(first,now.minusSeconds(1));alert.clearExpiredLease(now);UUID second=UUID.randomUUID();alert.claim(second,now.plusSeconds(60));service.fail(alert.getId(),second,2,Duration.ofSeconds(10));verify(evaluations).save(argThat(x->"FAILED".equals(x.getOutcome())&&x.getAttemptCount()==2));assertThat(alert.getLastOutcome()).isEqualTo("FAILED");assertThat(alert.getAttemptCount()).isZero();}
}
