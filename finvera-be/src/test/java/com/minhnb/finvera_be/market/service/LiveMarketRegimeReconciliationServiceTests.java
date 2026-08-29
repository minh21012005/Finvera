package com.minhnb.finvera_be.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.domain.breadth.BreadthCalculator;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.regime.MarketRegimeV1;
import com.minhnb.finvera_be.market.entity.MarketIndexEntity;
import com.minhnb.finvera_be.market.entity.MarketIndexSnapshotEntity;
import com.minhnb.finvera_be.market.repository.MarketIndexRepository;
import com.minhnb.finvera_be.market.repository.MarketIndexSnapshotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LiveMarketRegimeReconciliationServiceTests {
    @Mock MarketIndexRepository indexes;
    @Mock MarketIndexSnapshotRepository snapshots;
    @Mock RegimeAssessmentService assessments;

    @Test
    void persistsReasonCodedWithholdingWithCurrentAndHistoricalInputLinks() {
        UUID indexId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 8, 24);
        when(indexes.findByCode("VN_INDEX")).thenReturn(Optional.of(new MarketIndexEntity(
                indexId, "VN_INDEX", "VNINDEX", "VN-Index", "HOSE", date.minusYears(10), null)));
        when(snapshots.findAcceptedDailyHistory(indexId, "TCBS_IFLASH_MARKET_DATA", date))
                .thenReturn(history(date, 21));
        var service = new LiveMarketRegimeReconciliationService(indexes, snapshots, assessments);
        var breadth = new BreadthService.Snapshot(UUID.randomUUID(), date, Instant.parse("2026-08-24T03:00:00Z"),
                DataStatus.CURRENT, "LIVE", new BreadthCalculator.Result(300, 200, 100, 0, 600, List.of()), "provider", "a".repeat(64));

        service.reconcile(date, breadth);

        ArgumentCaptor<RegimeAssessmentService.AssessmentCommand> command =
                ArgumentCaptor.forClass(RegimeAssessmentService.AssessmentCommand.class);
        verify(assessments).persist(command.capture());
        assertThat(command.getValue().ruleVersion()).isEqualTo("market-regime-v2");
        assertThat(command.getValue().assessmentBasis()).isEqualTo("LIVE");
        assertThat(command.getValue().assessment().label()).isNull();
        assertThat(command.getValue().assessment().reasonCodes()).contains(
                "INSUFFICIENT_COMPONENT_COMPLETENESS", "TREND_COMPONENT_UNAVAILABLE");
        assertThat(command.getValue().inputLinks()).extracting(RegimeAssessmentService.InputLink::inputRole)
                .containsExactly("VN_INDEX_CURRENT", "BREADTH_CURRENT", "VN_INDEX_DAILY_HISTORY");
        assertThat(command.getValue().inputLinks().getLast().inputSetHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void publishesV2WhenAcceptedHistoryAndAggregateBreadthAreSufficient() {
        UUID indexId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 8, 24);
        when(indexes.findByCode("VN_INDEX")).thenReturn(Optional.of(new MarketIndexEntity(
                indexId, "VN_INDEX", "VNINDEX", "VN-Index", "HOSE", date.minusYears(10), null)));
        when(snapshots.findAcceptedDailyHistory(indexId, "TCBS_IFLASH_MARKET_DATA", date))
                .thenReturn(history(date, 253));
        var service = new LiveMarketRegimeReconciliationService(indexes, snapshots, assessments);
        var breadth = new BreadthService.Snapshot(UUID.randomUUID(), date, Instant.parse("2026-08-24T03:00:00Z"),
                DataStatus.CURRENT, "LIVE", new BreadthCalculator.Result(450, 150, 100, 0, 700, List.of()), "provider", "a".repeat(64));

        service.reconcile(date, breadth);

        ArgumentCaptor<RegimeAssessmentService.AssessmentCommand> command =
                ArgumentCaptor.forClass(RegimeAssessmentService.AssessmentCommand.class);
        verify(assessments).persist(command.capture());
        assertThat(command.getValue().ruleVersion()).isEqualTo("market-regime-v2");
        assertThat(command.getValue().assessmentBasis()).isEqualTo("LIVE");
        assertThat(command.getValue().assessment().label()).isNotNull();
        assertThat(command.getValue().assessment().score()).isNotNull();
        assertThat(command.getValue().assessment().reasonCodes())
                .doesNotContain("BREADTH_SMA50_COVERAGE_UNAVAILABLE", "LIQUIDITY_HISTORY_UNAVAILABLE");
    }

    @Test
    void withholdsTheBreadthComponentWhenClassifiedCoverageIsBelowTheConfiguredFloor() {
        UUID indexId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 8, 24);
        when(indexes.findByCode("VN_INDEX")).thenReturn(Optional.of(new MarketIndexEntity(
                indexId, "VN_INDEX", "VNINDEX", "VN-Index", "HOSE", date.minusYears(10), null)));
        when(snapshots.findAcceptedDailyHistory(indexId, "TCBS_IFLASH_MARKET_DATA", date))
                .thenReturn(history(date, 253));
        var service = new LiveMarketRegimeReconciliationService(indexes, snapshots, assessments);
        // One advancing instrument out of an eligible universe of 700: previously
        // this scored BREADTH = 100 at weight 0.25; under the 0.5 coverage floor
        // the component is withheld and market-regime-v2's own mandatory-input
        // rule withholds the assessment with its standard disclosure.
        var breadth = new BreadthService.Snapshot(UUID.randomUUID(), date, Instant.parse("2026-08-24T03:00:00Z"),
                DataStatus.PARTIAL, "LIVE", new BreadthCalculator.Result(1, 0, 0, 699, 700,
                        List.of("MISSING_PRICE")), "provider", "a".repeat(64));

        service.reconcile(date, breadth);

        ArgumentCaptor<RegimeAssessmentService.AssessmentCommand> command =
                ArgumentCaptor.forClass(RegimeAssessmentService.AssessmentCommand.class);
        verify(assessments).persist(command.capture());
        assertThat(command.getValue().assessment().label()).isNull();
        assertThat(command.getValue().assessment().reasonCodes())
                .contains("AGGREGATE_BREADTH_COMPONENT_UNAVAILABLE");
    }

    @Test
    void admitsTheBreadthComponentExactlyAtTheConfiguredFloor() {
        UUID indexId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 8, 24);
        when(indexes.findByCode("VN_INDEX")).thenReturn(Optional.of(new MarketIndexEntity(
                indexId, "VN_INDEX", "VNINDEX", "VN-Index", "HOSE", date.minusYears(10), null)));
        when(snapshots.findAcceptedDailyHistory(indexId, "TCBS_IFLASH_MARKET_DATA", date))
                .thenReturn(history(date, 253));
        var service = new LiveMarketRegimeReconciliationService(indexes, snapshots, assessments,
                new java.math.BigDecimal("0.5"));
        // 350 classified of 700 eligible = exactly the floor: admitted.
        var breadth = new BreadthService.Snapshot(UUID.randomUUID(), date, Instant.parse("2026-08-24T03:00:00Z"),
                DataStatus.PARTIAL, "LIVE", new BreadthCalculator.Result(200, 100, 50, 350, 700,
                        List.of("MISSING_PRICE")), "provider", "a".repeat(64));

        service.reconcile(date, breadth);

        ArgumentCaptor<RegimeAssessmentService.AssessmentCommand> command =
                ArgumentCaptor.forClass(RegimeAssessmentService.AssessmentCommand.class);
        verify(assessments).persist(command.capture());
        assertThat(command.getValue().assessment().label()).isNotNull();
        assertThat(command.getValue().assessment().reasonCodes())
                .doesNotContain("AGGREGATE_BREADTH_COMPONENT_UNAVAILABLE");
    }

    @Test
    void skipsReadRepairWhenLatestAssessmentAlreadyCoversBreadth() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        var service = new LiveMarketRegimeReconciliationService(indexes, snapshots, assessments);
        UUID breadthId = UUID.randomUUID();
        var breadth = new BreadthService.Snapshot(breadthId, date, Instant.parse("2026-08-24T03:00:00Z"),
                DataStatus.CURRENT, "LIVE", new BreadthCalculator.Result(300, 200, 100, 0, 600, List.of()), "provider", "a".repeat(64));
        var latest = new RegimeAssessmentService.Snapshot(
                date, Instant.parse("2026-08-24T03:00:00Z"), "market-regime-v2", "LIVE",
                new com.minhnb.finvera_be.market.domain.regime.RegimeAssessment(
                        DataStatus.CURRENT, com.minhnb.finvera_be.market.domain.model.MarketTypes.RegimeLabel.EARLY_BULL,
                        62, 90, BigDecimal.valueOf(100), BigDecimal.valueOf(80), BigDecimal.valueOf(70),
                        false, List.of(), List.of(new com.minhnb.finvera_be.market.domain.regime.RegimeAssessment.SupportingFactor(
                        MarketRegimeV1.Component.BREADTH,
                        com.minhnb.finvera_be.market.domain.model.MarketTypes.FactorDirection.POSITIVE,
                        BigDecimal.valueOf(60), BigDecimal.valueOf(0.25), BigDecimal.valueOf(0.25),
                        BigDecimal.valueOf(15)))));
        when(assessments.latestFor(date, "LIVE")).thenReturn(Optional.of(latest));
        when(assessments.usesBreadthSnapshot(latest, breadthId)).thenReturn(true);

        service.reconcileIfMissingOrOlder(date, breadth);

        verify(indexes, never()).findByCode(any());
        verify(assessments, never()).persist(any());
    }

    @Test
    void readRepairReconcilesWhenPublishedAssessmentReferencesOlderBreadthSnapshot() {
        UUID indexId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 8, 24);
        UUID newBreadthId = UUID.randomUUID();
        var latest = new RegimeAssessmentService.Snapshot(
                date, Instant.parse("2026-08-24T08:02:00Z"), "market-regime-v2", "EOD",
                new com.minhnb.finvera_be.market.domain.regime.RegimeAssessment(
                        DataStatus.PARTIAL, com.minhnb.finvera_be.market.domain.model.MarketTypes.RegimeLabel.SIDEWAYS,
                        53, 80, BigDecimal.valueOf(100), BigDecimal.valueOf(80), BigDecimal.valueOf(70),
                        false, List.of(), List.of(new com.minhnb.finvera_be.market.domain.regime.RegimeAssessment.SupportingFactor(
                        MarketRegimeV1.Component.BREADTH,
                        com.minhnb.finvera_be.market.domain.model.MarketTypes.FactorDirection.NEGATIVE,
                        BigDecimal.valueOf(36), BigDecimal.valueOf(0.25), BigDecimal.valueOf(0.25),
                        BigDecimal.valueOf(9)))));
        when(assessments.latestFor(date, "EOD")).thenReturn(Optional.of(latest));
        when(indexes.findByCode("VN_INDEX")).thenReturn(Optional.of(new MarketIndexEntity(
                indexId, "VN_INDEX", "VNINDEX", "VN-Index", "HOSE", date.minusYears(10), null)));
        when(snapshots.findAcceptedCompletedDailyHistory(indexId, "VNSTOCK%", date))
                .thenReturn(history(date, 253));
        var service = new LiveMarketRegimeReconciliationService(indexes, snapshots, assessments);
        var breadth = new BreadthService.Snapshot(newBreadthId, date, Instant.parse("2026-08-24T08:02:00Z"),
                DataStatus.CURRENT, "EOD", new BreadthCalculator.Result(450, 150, 100, 0, 700, List.of()),
                "breadth-universe-v1", "a".repeat(64));

        service.reconcileEndOfDayIfMissingOrOlder(date, breadth);

        ArgumentCaptor<RegimeAssessmentService.AssessmentCommand> command =
                ArgumentCaptor.forClass(RegimeAssessmentService.AssessmentCommand.class);
        verify(assessments).persist(command.capture());
        assertThat(command.getValue().assessmentBasis()).isEqualTo("EOD");
        assertThat(command.getValue().assessment().dataStatus()).isEqualTo(DataStatus.CURRENT);
    }

    @Test
    void readRepairReconcilesWhenLatestV2IsStillWithheld() {
        UUID indexId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 8, 24);
        when(assessments.latestFor(date, "LIVE")).thenReturn(Optional.of(new RegimeAssessmentService.Snapshot(
                date, Instant.parse("2026-08-24T03:00:00Z"), "market-regime-v2", "LIVE",
                new com.minhnb.finvera_be.market.domain.regime.RegimeAssessment(
                        DataStatus.PARTIAL, null, null, null, BigDecimal.ZERO, null, null,
                        false, List.of("TREND_COMPONENT_UNAVAILABLE"), List.of()))));
        when(indexes.findByCode("VN_INDEX")).thenReturn(Optional.of(new MarketIndexEntity(
                indexId, "VN_INDEX", "VNINDEX", "VN-Index", "HOSE", date.minusYears(10), null)));
        when(snapshots.findAcceptedDailyHistory(indexId, "TCBS_IFLASH_MARKET_DATA", date))
                .thenReturn(history(date, 253));
        var service = new LiveMarketRegimeReconciliationService(indexes, snapshots, assessments);
        var breadth = new BreadthService.Snapshot(UUID.randomUUID(), date, Instant.parse("2026-08-24T03:00:00Z"),
                DataStatus.CURRENT, "LIVE", new BreadthCalculator.Result(450, 150, 100, 0, 700, List.of()), "provider", "a".repeat(64));

        service.reconcileIfMissingOrOlder(date, breadth);

        ArgumentCaptor<RegimeAssessmentService.AssessmentCommand> command =
                ArgumentCaptor.forClass(RegimeAssessmentService.AssessmentCommand.class);
        verify(assessments).persist(command.capture());
        assertThat(command.getValue().ruleVersion()).isEqualTo("market-regime-v2");
        assertThat(command.getValue().assessmentBasis()).isEqualTo("LIVE");
        assertThat(command.getValue().assessment().label()).isNotNull();
    }

    @Test
    void readRepairReconcilesWhenLatestAssessmentUsesOlderRuleVersion() {
        UUID indexId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 8, 24);
        when(assessments.latestFor(date, "LIVE")).thenReturn(Optional.of(new RegimeAssessmentService.Snapshot(
                date, Instant.parse("2026-08-24T03:00:00Z"), "market-regime-v1", "LIVE",
                new com.minhnb.finvera_be.market.domain.regime.RegimeAssessment(
                        DataStatus.PARTIAL, null, null, null, BigDecimal.ZERO, null, null,
                        false, List.of("BREADTH_SMA50_COVERAGE_UNAVAILABLE"), List.of()))));
        when(indexes.findByCode("VN_INDEX")).thenReturn(Optional.of(new MarketIndexEntity(
                indexId, "VN_INDEX", "VNINDEX", "VN-Index", "HOSE", date.minusYears(10), null)));
        when(snapshots.findAcceptedDailyHistory(indexId, "TCBS_IFLASH_MARKET_DATA", date))
                .thenReturn(history(date, 253));
        var service = new LiveMarketRegimeReconciliationService(indexes, snapshots, assessments);
        var breadth = new BreadthService.Snapshot(UUID.randomUUID(), date, Instant.parse("2026-08-24T03:00:00Z"),
                DataStatus.CURRENT, "LIVE", new BreadthCalculator.Result(450, 150, 100, 0, 700, List.of()), "provider", "a".repeat(64));

        service.reconcileIfMissingOrOlder(date, breadth);

        ArgumentCaptor<RegimeAssessmentService.AssessmentCommand> command =
                ArgumentCaptor.forClass(RegimeAssessmentService.AssessmentCommand.class);
        verify(assessments).persist(command.capture());
        assertThat(command.getValue().ruleVersion()).isEqualTo("market-regime-v2");
        assertThat(command.getValue().assessmentBasis()).isEqualTo("LIVE");
    }

    @Test
    void endOfDayReconciliationUsesOnlyCompletedVnstockHistoryAndStoresEodBasis() {
        UUID indexId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 8, 24);
        when(indexes.findByCode("VN_INDEX")).thenReturn(Optional.of(new MarketIndexEntity(
                indexId, "VN_INDEX", "VNINDEX", "VN-Index", "HOSE", date.minusYears(10), null)));
        when(snapshots.findAcceptedCompletedDailyHistory(indexId, "VNSTOCK%", date))
                .thenReturn(history(date, 253));
        var service = new LiveMarketRegimeReconciliationService(indexes, snapshots, assessments);
        var breadth = new BreadthService.Snapshot(UUID.randomUUID(), date, Instant.parse("2026-08-24T08:02:00Z"),
                DataStatus.CURRENT, "EOD", new BreadthCalculator.Result(450, 150, 100, 0, 700, List.of()),
                "breadth-universe-v1", "a".repeat(64));

        service.reconcileEndOfDay(date, breadth);

        ArgumentCaptor<RegimeAssessmentService.AssessmentCommand> command =
                ArgumentCaptor.forClass(RegimeAssessmentService.AssessmentCommand.class);
        verify(assessments).persist(command.capture());
        assertThat(command.getValue().ruleVersion()).isEqualTo("market-regime-v2");
        assertThat(command.getValue().assessmentBasis()).isEqualTo("EOD");
        assertThat(command.getValue().assessment().label()).isNotNull();
    }

    private static List<MarketIndexSnapshotEntity> history(LocalDate end, int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(offset -> {
            LocalDate day = end.minusDays(count - offset - 1L);
            Instant at = day.atTime(8, 0).atZone(java.time.ZoneOffset.UTC).toInstant();
            return new MarketIndexSnapshotEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), day, at, at,
                    "CLOSED", new BigDecimal("1700").add(BigDecimal.valueOf(offset)), new BigDecimal("1699"),
                    BigDecimal.ONE, new BigDecimal("0.01"), 100L, null, "VNSTOCK_KBS", 1, null);
        }).toList();
    }
}
