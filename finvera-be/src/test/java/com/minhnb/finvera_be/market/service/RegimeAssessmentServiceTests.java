package com.minhnb.finvera_be.market.service;

import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.CURRENT;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.PARTIAL;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.RegimeLabel.BULL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.domain.regime.RegimeAssessment;
import com.minhnb.finvera_be.market.entity.MarketRegimeAssessmentEntity;
import com.minhnb.finvera_be.market.entity.MarketRegimeAssessmentInputEntity;
import com.minhnb.finvera_be.market.repository.RegimeAssessmentInputRepository;
import com.minhnb.finvera_be.market.repository.RegimeAssessmentRepository;
import com.minhnb.finvera_be.market.repository.RegimeFactorRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class RegimeAssessmentServiceTests {

    private static final LocalDate TRADING_DATE = LocalDate.of(2026, 8, 17);
    private static final Instant AS_OF = Instant.parse("2026-08-17T03:00:00Z");

    @Test
    void persistsAnImmutableAssessmentWithExactCurrentAndHistoricalInputLinks() {
        var assessments = Mockito.mock(RegimeAssessmentRepository.class);
        var inputs = Mockito.mock(RegimeAssessmentInputRepository.class);
        var factors = Mockito.mock(RegimeFactorRepository.class);
        when(assessments.save(any(MarketRegimeAssessmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = service(assessments, inputs, factors);
        UUID currentIndexId = UUID.randomUUID();
        UUID currentBreadthId = UUID.randomUUID();
        String historyHash = "a".repeat(64);

        var stored = service.persist(new RegimeAssessmentService.AssessmentCommand(
                TRADING_DATE, AS_OF, publishedAssessment(), null,
                List.of(
                        RegimeAssessmentService.InputLink.indexSnapshot("VN_INDEX_CURRENT", currentIndexId),
                        RegimeAssessmentService.InputLink.breadthSnapshot("BREADTH_CURRENT", currentBreadthId),
                        RegimeAssessmentService.InputLink.inputSet("VN_INDEX_HISTORY", historyHash)),
                List.of(new RegimeAssessmentService.SourceValue("TCBS", "VN_INDEX_CURRENT", decimal("1500.000000")))));

        assertThat(stored.assessment().score()).isEqualTo(80);
        ArgumentCaptor<MarketRegimeAssessmentEntity> assessmentCaptor = ArgumentCaptor.forClass(MarketRegimeAssessmentEntity.class);
        verify(assessments).save(assessmentCaptor.capture());
        assertThat(assessmentCaptor.getValue().getRuleVersion()).isEqualTo("market-regime-v1");
        assertThat(assessmentCaptor.getValue().getSupersedesId()).isNull();
        assertThat(assessmentCaptor.getValue().getCalculatedAt()).isEqualTo(Instant.parse("2026-08-17T03:05:00Z"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarketRegimeAssessmentInputEntity>> inputCaptor = ArgumentCaptor.forClass(List.class);
        verify(inputs).saveAll(inputCaptor.capture());
        assertThat(inputCaptor.getValue()).extracting(MarketRegimeAssessmentInputEntity::getInputRole)
                .containsExactly("VN_INDEX_CURRENT", "BREADTH_CURRENT", "VN_INDEX_HISTORY");
        assertThat(inputCaptor.getValue().get(0).getIndexSnapshotId()).isEqualTo(currentIndexId);
        assertThat(inputCaptor.getValue().get(1).getBreadthSnapshotId()).isEqualTo(currentBreadthId);
        assertThat(inputCaptor.getValue().get(2).getInputSetHash()).isEqualTo(historyHash);
    }

    @Test
    void createsANewRevisionLinkedToThePreviousAssessmentWhenCorrectedHistoryIsReplayed() {
        var assessments = Mockito.mock(RegimeAssessmentRepository.class);
        var inputs = Mockito.mock(RegimeAssessmentInputRepository.class);
        var factors = Mockito.mock(RegimeFactorRepository.class);
        when(assessments.save(any(MarketRegimeAssessmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = service(assessments, inputs, factors);
        UUID supersededAssessmentId = UUID.randomUUID();

        var stored = service.persist(new RegimeAssessmentService.AssessmentCommand(
                TRADING_DATE, AS_OF.plusSeconds(60), publishedAssessment(), supersededAssessmentId,
                List.of(RegimeAssessmentService.InputLink.inputSet("VN_INDEX_HISTORY", "b".repeat(64))),
                List.of(new RegimeAssessmentService.SourceValue("TCBS", "VN_INDEX_HISTORY", decimal("1501.000000")))));

        assertThat(stored.id()).isNotEqualTo(supersededAssessmentId);
        ArgumentCaptor<MarketRegimeAssessmentEntity> assessmentCaptor = ArgumentCaptor.forClass(MarketRegimeAssessmentEntity.class);
        verify(assessments).save(assessmentCaptor.capture());
        assertThat(assessmentCaptor.getValue().getSupersedesId()).isEqualTo(supersededAssessmentId);
        assertThat(assessmentCaptor.getValue().getAsOf()).isEqualTo(AS_OF.plusSeconds(60));
    }

    @Test
    void withholdsSourceConflictInsteadOfAveragingConflictingValues() {
        var assessments = Mockito.mock(RegimeAssessmentRepository.class);
        var inputs = Mockito.mock(RegimeAssessmentInputRepository.class);
        var factors = Mockito.mock(RegimeFactorRepository.class);
        when(assessments.save(any(MarketRegimeAssessmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = service(assessments, inputs, factors);

        var stored = service.persist(new RegimeAssessmentService.AssessmentCommand(
                TRADING_DATE, AS_OF, publishedAssessment(), null, List.of(),
                List.of(
                        new RegimeAssessmentService.SourceValue("TCBS", "VN_INDEX_CURRENT", decimal("1500.000000")),
                        new RegimeAssessmentService.SourceValue("VNSTOCK", "VN_INDEX_CURRENT", decimal("1501.000000")))));

        assertThat(stored.assessment().dataStatus()).isEqualTo(PARTIAL);
        assertThat(stored.assessment().label()).isNull();
        assertThat(stored.assessment().score()).isNull();
        assertThat(stored.assessment().confidence()).isNull();
        assertThat(stored.assessment().reasonCodes()).contains("SOURCE_CONFLICT");
        ArgumentCaptor<MarketRegimeAssessmentEntity> assessmentCaptor = ArgumentCaptor.forClass(MarketRegimeAssessmentEntity.class);
        verify(assessments).save(assessmentCaptor.capture());
        assertThat(assessmentCaptor.getValue().getScore()).isNull();
        assertThat(assessmentCaptor.getValue().getReasonCodes()).containsExactly("SOURCE_CONFLICT");
    }

    private static RegimeAssessmentService service(
            RegimeAssessmentRepository assessments, RegimeAssessmentInputRepository inputs, RegimeFactorRepository factors) {
        return new RegimeAssessmentService(assessments, inputs, factors,
                Clock.fixed(Instant.parse("2026-08-17T03:05:00Z"), ZoneOffset.UTC));
    }

    private static RegimeAssessment publishedAssessment() {
        return new RegimeAssessment(CURRENT, BULL, 80, 90,
                decimal("100"), decimal("100"), decimal("100"), false, List.of(), List.of());
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
