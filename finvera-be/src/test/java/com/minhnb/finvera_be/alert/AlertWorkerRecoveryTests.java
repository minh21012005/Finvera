package com.minhnb.finvera_be.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.minhnb.finvera_be.alert.entity.AlertDefinitionEntity;
import com.minhnb.finvera_be.alert.repository.AlertDefinitionRepository;
import com.minhnb.finvera_be.alert.service.AlertClaimService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AlertWorkerRecoveryTests {
    @Test void claimIsBoundedAndAssignsLeaseInsideClaimService(){var repo=mock(AlertDefinitionRepository.class);Instant now=Instant.parse("2026-09-16T00:00:00Z");var alert=new AlertDefinitionEntity(UUID.randomUUID(),UUID.randomUUID(),"VCB","PRICE_ABOVE",new ObjectMapper().createObjectNode(),"summary",now);when(repo.findAndLockDue(now,50)).thenReturn(List.of(alert));var claims=new AlertClaimService(repo,Clock.fixed(now,ZoneOffset.UTC)).claim(50,Duration.ofSeconds(60));assertThat(claims).extracting(AlertClaimService.Claim::alertId).containsExactly(alert.getId());assertThat(claims.getFirst().leaseToken()).isEqualTo(alert.getLeaseToken());assertThat(alert.getLeaseUntil()).isEqualTo(now.plusSeconds(60));assertThat(alert.getAttemptCount()).isEqualTo((short)1);verify(repo).findAndLockDue(now,50);}
    @Test void secondExpiredLeaseBecomesTerminalRecoveryWorkInsteadOfThirdAttempt(){var repo=mock(AlertDefinitionRepository.class);Instant now=Instant.parse("2026-09-16T00:00:00Z");var alert=new AlertDefinitionEntity(UUID.randomUUID(),UUID.randomUUID(),"VCB","PRICE_ABOVE",new ObjectMapper().createObjectNode(),"summary",now);alert.claim(UUID.randomUUID(),now.minusSeconds(120));alert.clearExpiredLease(now.minusSeconds(60));alert.claim(UUID.randomUUID(),now.minusSeconds(1));when(repo.findAndLockExpired(now,50)).thenReturn(List.of(alert));var terminal=new AlertClaimService(repo,Clock.fixed(now,ZoneOffset.UTC)).recoverExpired(50,2,Duration.ofSeconds(60));assertThat(terminal).hasSize(1);assertThat(terminal.getFirst().leaseToken()).isEqualTo(alert.getLeaseToken());assertThat(alert.getAttemptCount()).isEqualTo((short)2);assertThat(alert.getLeaseUntil()).isEqualTo(now.plusSeconds(60));}
}
