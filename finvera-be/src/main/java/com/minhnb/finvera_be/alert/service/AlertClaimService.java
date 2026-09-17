package com.minhnb.finvera_be.alert.service;

import com.minhnb.finvera_be.alert.repository.AlertDefinitionRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transaction boundary that makes worker claims exclusive across application instances. */
@Service
public class AlertClaimService {
    public record Claim(UUID alertId,UUID leaseToken) {}
    private final AlertDefinitionRepository repository;
    private final Clock clock;
    public AlertClaimService(AlertDefinitionRepository repository, Clock clock) {this.repository=repository;this.clock=clock;}
    @Transactional public List<Claim> claim(int limit,Duration leaseDuration){Instant now=clock.instant();var due=repository.findAndLockDue(now,limit);var claims=new ArrayList<Claim>();for(var alert:due){UUID token=UUID.randomUUID();alert.claim(token,now.plus(leaseDuration));claims.add(new Claim(alert.getId(),token));}return List.copyOf(claims);}
    @Transactional public List<Claim> recoverExpired(int limit,int maxAttempts,Duration holdDuration){Instant now=clock.instant();var terminal=new ArrayList<Claim>();for(var alert:repository.findAndLockExpired(now,limit)){if(alert.getAttemptCount()>=maxAttempts){alert.holdLease(now.plus(holdDuration));terminal.add(new Claim(alert.getId(),alert.getLeaseToken()));}else alert.clearExpiredLease(now);}return List.copyOf(terminal);}
}
