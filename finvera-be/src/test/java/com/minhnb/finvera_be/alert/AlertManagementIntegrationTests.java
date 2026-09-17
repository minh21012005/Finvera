package com.minhnb.finvera_be.alert;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.minhnb.finvera_be.alert.dto.AlertDtos.CreateAlertRequest;
import com.minhnb.finvera_be.alert.entity.AlertDefinitionEntity;
import com.minhnb.finvera_be.alert.repository.*;
import com.minhnb.finvera_be.alert.service.*;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.portfolio.service.PortfolioAlertDataService;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

class AlertManagementIntegrationTests {
    private final UUID owner=UUID.randomUUID();
    private final AlertDefinitionRepository definitions=mock(AlertDefinitionRepository.class);
    private final AlertEvaluationRepository evaluations=mock(AlertEvaluationRepository.class);
    private final MarketReferenceDataService market=mock(MarketReferenceDataService.class);
    private final PortfolioAlertDataService portfolios=mock(PortfolioAlertDataService.class);
    private final OwnerScopedAlertAccess access=mock(OwnerScopedAlertAccess.class);
    private final EntityManager entityManager=mock(EntityManager.class);
    private final Query lockQuery=mock(Query.class);
    private final ObjectMapper json=new ObjectMapper();
    private AlertManagementService service;

    @BeforeEach void setUp(){when(access.getAuthenticatedOwnerId()).thenReturn(owner);when(entityManager.createNativeQuery(anyString())).thenReturn(lockQuery);when(lockQuery.setParameter(anyInt(),any())).thenReturn(lockQuery);when(lockQuery.getSingleResult()).thenReturn(null);service=new AlertManagementService(definitions,evaluations,market,portfolios,access,entityManager,Clock.fixed(Instant.parse("2026-09-16T00:00:00Z"),ZoneOffset.UTC));}

    @Test void retainedQuotaIsCheckedUnderOwnerLock(){when(definitions.countByOwnerIdAndDeletedAtIsNull(owner)).thenReturn(100L);assertThatThrownBy(()->service.create(new CreateAlertRequest("VCB",json.readTree("{\"type\":\"PRICE_ABOVE\",\"symbol\":\"VCB\",\"threshold\":\"1\"}")))).isInstanceOf(AlertExceptions.AlertQuotaException.class);verify(entityManager).createNativeQuery(contains("pg_advisory_xact_lock"));verify(definitions,never()).save(any());}

    @Test void foreignPortfolioAndUnknownPortfolioAreIndistinguishable(){UUID portfolio=UUID.randomUUID();when(definitions.countByOwnerIdAndDeletedAtIsNull(owner)).thenReturn(0L);when(definitions.countByOwnerIdAndEnabledTrueAndDeletedAtIsNull(owner)).thenReturn(0L);when(portfolios.existsOwned(owner,portfolio)).thenReturn(false);var request=new CreateAlertRequest("Tỷ trọng",json.readTree("{\"type\":\"PORTFOLIO_CONCENTRATION_ABOVE\",\"portfolioId\":\""+portfolio+"\",\"thresholdPercent\":\"50\"}"));assertThatThrownBy(()->service.create(request)).isInstanceOf(AlertExceptions.AlertValidationException.class).hasMessage("TARGET_NOT_AVAILABLE");}

    @Test void arbitraryConditionFieldsAreRejectedBeforePersistence(){when(definitions.countByOwnerIdAndDeletedAtIsNull(owner)).thenReturn(0L);when(definitions.countByOwnerIdAndEnabledTrueAndDeletedAtIsNull(owner)).thenReturn(0L);var request=new CreateAlertRequest("VCB",json.readTree("{\"type\":\"PRICE_ABOVE\",\"symbol\":\"VCB\",\"threshold\":\"1\",\"command\":\"buy\"}"));assertThatThrownBy(()->service.create(request)).isInstanceOf(AlertExceptions.AlertValidationException.class).hasMessage("CONDITION_INVALID");verify(definitions,never()).save(any());}

    @Test void repeatedEnableIsIdempotentAndDoesNotResetState(){UUID id=UUID.randomUUID();var alert=new AlertDefinitionEntity(id,owner,"VCB","PRICE_ABOVE",json.createObjectNode(),"summary",Instant.parse("2026-09-15T00:00:00Z"));when(definitions.findByIdAndOwnerIdAndDeletedAtIsNull(id,owner)).thenReturn(Optional.of(alert));when(evaluations.findFirstByAlertIdOrderByEvaluatedAtDescIdDesc(id)).thenReturn(Optional.empty());service.state(id,true);verify(entityManager,never()).createNativeQuery(anyString());verify(definitions,never()).countByOwnerIdAndEnabledTrueAndDeletedAtIsNull(owner);}
}
