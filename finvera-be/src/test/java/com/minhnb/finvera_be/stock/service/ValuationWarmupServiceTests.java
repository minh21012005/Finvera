package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.stock.domain.valuation.ValuationV1;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.entity.ValuationAssessmentEntity;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.ValuationAssessmentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValuationWarmupServiceTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 26);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            TODAY.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));

    private final EquityProfileRepository equityProfiles = mock(EquityProfileRepository.class);
    private final MarketReferenceDataService referenceData = mock(MarketReferenceDataService.class);
    private final ValuationAssessmentRepository assessments = mock(ValuationAssessmentRepository.class);
    private final ValuationService valuations = mock(ValuationService.class);
    private final com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository dailyBars =
            mock(com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository.class);
    private final com.minhnb.finvera_be.stock.repository.FundamentalReportRepository reports =
            mock(com.minhnb.finvera_be.stock.repository.FundamentalReportRepository.class);
    private final ValuationWarmupService warmup =
            new ValuationWarmupService(equityProfiles, referenceData, assessments, valuations, dailyBars, reports, false, FIXED_CLOCK);

    @Test
    void warmsEveryListedSymbolThroughTheCanonicalValuationService() {
        UUID fptId = UUID.randomUUID();
        UUID vnmId = UUID.randomUUID();
        when(equityProfiles.findByEffectiveToIsNullAndListingStatus("LISTED")).thenReturn(List.of(
                profile(fptId), profile(vnmId)));
        when(referenceData.findInstrumentsByIds(anyCollection())).thenReturn(List.of(
                new InstrumentReference(fptId, "HOSE", "FPT", "COMMON_STOCK", "ACTIVE"),
                new InstrumentReference(vnmId, "HOSE", "VNM", "COMMON_STOCK", "ACTIVE")));
        when(assessments.findLatestCurrentByInstrumentIdInAndRuleVersion(anyCollection(), eq(ValuationV1.RULE_VERSION)))
                .thenReturn(List.of());
        when(valuations.findBySymbol("FPT")).thenReturn(Optional.of(mock(ValuationService.StockValuation.class)));
        when(valuations.findBySymbol("VNM")).thenReturn(Optional.empty());

        var summary = warmup.warmUp();

        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.succeeded()).isEqualTo(1);
        assertThat(summary.unavailable()).isEqualTo(1);
        assertThat(summary.skipped()).isZero();
        assertThat(summary.failed()).isZero();
        verify(valuations).findBySymbol("FPT");
        verify(valuations).findBySymbol("VNM");
    }

    @Test
    void skipsSymbolsAlreadyAssessedToday() {
        UUID fptId = UUID.randomUUID();
        when(equityProfiles.findByEffectiveToIsNullAndListingStatus("LISTED")).thenReturn(List.of(profile(fptId)));
        when(referenceData.findInstrumentsByIds(anyCollection())).thenReturn(List.of(
                new InstrumentReference(fptId, "HOSE", "FPT", "COMMON_STOCK", "ACTIVE")));
        ValuationAssessmentEntity existing = mock(ValuationAssessmentEntity.class);
        when(existing.getInstrumentId()).thenReturn(fptId);
        when(existing.getAsOfTradingDate()).thenReturn(TODAY);
        when(assessments.findLatestCurrentByInstrumentIdInAndRuleVersion(anyCollection(), eq(ValuationV1.RULE_VERSION)))
                .thenReturn(List.of(existing));

        var summary = warmup.warmUp();

        verify(valuations, never()).findBySymbol(any());
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.succeeded()).isZero();
    }

    @Test
    void recomputesWhenABarOrReportWasAcceptedAfterTodaysAssessment() {
        // Q-48: assessed this morning, then a report (or corrected bar) was imported.
        UUID fptId = UUID.randomUUID();
        when(equityProfiles.findByEffectiveToIsNullAndListingStatus("LISTED")).thenReturn(List.of(profile(fptId)));
        when(referenceData.findInstrumentsByIds(anyCollection())).thenReturn(List.of(
                new InstrumentReference(fptId, "HOSE", "FPT", "COMMON_STOCK", "ACTIVE")));
        ValuationAssessmentEntity existing = mock(ValuationAssessmentEntity.class);
        when(existing.getInstrumentId()).thenReturn(fptId);
        when(existing.getAsOfTradingDate()).thenReturn(TODAY);
        when(existing.getCalculatedAt()).thenReturn(java.time.Instant.parse("2026-08-26T01:00:00Z"));
        when(assessments.findLatestCurrentByInstrumentIdInAndRuleVersion(anyCollection(), eq(ValuationV1.RULE_VERSION)))
                .thenReturn(List.of(existing));
        when(reports.findLatestAcceptedAtByInstrumentIdIn(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[] {fptId, java.time.Instant.parse("2026-08-26T03:00:00Z")}));
        when(valuations.findBySymbol("FPT")).thenReturn(Optional.of(mock(ValuationService.StockValuation.class)));

        var summary = warmup.warmUp();

        verify(valuations).findBySymbol("FPT");
        assertThat(summary.succeeded()).isEqualTo(1);
        assertThat(summary.skipped()).isZero();
    }

    @Test
    void forceRecomputesEvenWhenAssessedTodayWithUnchangedInputs() {
        UUID fptId = UUID.randomUUID();
        var forced = new ValuationWarmupService(equityProfiles, referenceData, assessments, valuations, dailyBars, reports, true, FIXED_CLOCK);
        when(equityProfiles.findByEffectiveToIsNullAndListingStatus("LISTED")).thenReturn(List.of(profile(fptId)));
        when(referenceData.findInstrumentsByIds(anyCollection())).thenReturn(List.of(
                new InstrumentReference(fptId, "HOSE", "FPT", "COMMON_STOCK", "ACTIVE")));
        ValuationAssessmentEntity existing = mock(ValuationAssessmentEntity.class);
        when(existing.getInstrumentId()).thenReturn(fptId);
        when(existing.getAsOfTradingDate()).thenReturn(TODAY);
        when(existing.getCalculatedAt()).thenReturn(java.time.Instant.parse("2026-08-26T01:00:00Z"));
        when(assessments.findLatestCurrentByInstrumentIdInAndRuleVersion(anyCollection(), eq(ValuationV1.RULE_VERSION)))
                .thenReturn(List.of(existing));
        when(valuations.findBySymbol("FPT")).thenReturn(Optional.of(mock(ValuationService.StockValuation.class)));

        var summary = forced.warmUp();

        verify(valuations).findBySymbol("FPT");
        assertThat(summary.succeeded()).isEqualTo(1);
    }

    @Test
    void inputsRevisedSinceIsFalseWithoutNewerAcceptances() {
        var calc = java.time.Instant.parse("2026-08-26T01:00:00Z");
        assertThat(ValuationWarmupService.inputsRevisedSince(calc, calc.minusSeconds(60), null)).isFalse();
        assertThat(ValuationWarmupService.inputsRevisedSince(calc, null, calc.minusSeconds(1))).isFalse();
        assertThat(ValuationWarmupService.inputsRevisedSince(null, calc, calc)).isFalse();
        assertThat(ValuationWarmupService.inputsRevisedSince(calc, calc.plusSeconds(1), null)).isTrue();
    }

    private static EquityProfileEntity profile(UUID instrumentId) {
        return new EquityProfileEntity(UUID.randomUUID(), instrumentId, "Company", null,
                null, 1_000_000L, new BigDecimal("0.500000"), "LISTED",
                LocalDate.of(2024, 1, 1), null, "TEST", "1", null);
    }
}

