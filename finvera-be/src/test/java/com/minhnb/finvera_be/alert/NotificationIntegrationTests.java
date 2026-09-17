package com.minhnb.finvera_be.alert;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.minhnb.finvera_be.alert.entity.AlertNotificationEntity;
import com.minhnb.finvera_be.alert.repository.AlertNotificationRepository;
import com.minhnb.finvera_be.alert.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class NotificationIntegrationTests {
    @Test void markReadIsIdempotentAndOwnerScoped(){UUID owner=UUID.randomUUID(),id=UUID.randomUUID();var repo=mock(AlertNotificationRepository.class);var access=mock(OwnerScopedAlertAccess.class);when(access.getAuthenticatedOwnerId()).thenReturn(owner);Instant now=Instant.parse("2026-09-16T00:00:00Z");var json=new ObjectMapper().createObjectNode();var notification=new AlertNotificationEntity(id,owner,UUID.randomUUID(),UUID.randomUUID(),1L,null,"title","message",json,json,now.minusSeconds(10));when(repo.findByIdAndOwnerId(id,owner)).thenReturn(Optional.of(notification));var service=new NotificationService(repo,access,Clock.fixed(now,ZoneOffset.UTC));assertThat(service.read(id).readAt()).isEqualTo(now);assertThat(service.read(id).readAt()).isEqualTo(now);verify(repo,times(2)).findByIdAndOwnerId(id,owner);}

    @Test void foreignAndUnknownIdsUseSameNotFoundFailure(){UUID owner=UUID.randomUUID(),id=UUID.randomUUID();var repo=mock(AlertNotificationRepository.class);var access=mock(OwnerScopedAlertAccess.class);when(access.getAuthenticatedOwnerId()).thenReturn(owner);when(repo.findByIdAndOwnerId(id,owner)).thenReturn(Optional.empty());var service=new NotificationService(repo,access,Clock.systemUTC());assertThatThrownBy(()->service.get(id)).isInstanceOf(AlertExceptions.NotificationNotFoundException.class);}
}
