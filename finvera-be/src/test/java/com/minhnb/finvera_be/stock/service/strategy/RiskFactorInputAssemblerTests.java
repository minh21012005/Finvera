package com.minhnb.finvera_be.stock.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorResultRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorValueRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiskFactorInputAssemblerTests {

    @Mock MarketReferenceDataService referenceData;
    @Mock EquityDailyBarRepository dailyBars;
    @Mock TechnicalIndicatorResultRepository technicalResults;
    @Mock TechnicalIndicatorValueRepository technicalValues;

    @Test
    void usesPartialEndOfDayRegimeWhenItHasAPublishedScore() {
        UUID regimeId = UUID.randomUUID();
        when(referenceData.findCurrentRegimeAssessment("EOD")).thenReturn(Optional.of(
                new MarketReferenceDataService.RegimeAssessmentReference(
                        regimeId, LocalDate.of(2026, 8, 25), "EOD", 55, DataStatus.PARTIAL)));
        when(technicalResults.findLatestNCurrentByInstrumentIdInAndRuleVersionAndIndicatorCode(
                any(), any(), any(), anyInt())).thenReturn(List.of());
        when(dailyBars.findLatestNCurrentByInstrumentIdIn(any(), anyInt())).thenReturn(List.of());

        var assembler = new RiskFactorInputAssembler(referenceData, dailyBars, technicalResults, technicalValues);

        var context = assembler.build(UUID.randomUUID(), null, null);

        assertThat(context.inputs().regimeScore().available()).isTrue();
        assertThat(context.inputs().regimeScore().value()).isEqualByComparingTo("55");
        assertThat(context.regimeAssessmentId()).isEqualTo(regimeId);
    }

    @Test
    void rejectsStaleEndOfDayRegimeEvenWhenItHasAScore() {
        when(referenceData.findCurrentRegimeAssessment("EOD")).thenReturn(Optional.of(
                new MarketReferenceDataService.RegimeAssessmentReference(
                        UUID.randomUUID(), LocalDate.of(2026, 8, 10), "EOD", 55, DataStatus.STALE)));
        when(technicalResults.findLatestNCurrentByInstrumentIdInAndRuleVersionAndIndicatorCode(
                any(), any(), any(), anyInt())).thenReturn(List.of());
        when(dailyBars.findLatestNCurrentByInstrumentIdIn(any(), anyInt())).thenReturn(List.of());

        var assembler = new RiskFactorInputAssembler(referenceData, dailyBars, technicalResults, technicalValues);

        var context = assembler.build(UUID.randomUUID(), null, null);

        assertThat(context.inputs().regimeScore().available()).isFalse();
        assertThat(context.inputs().regimeScore().reasonCode()).isEqualTo("STALE");
        assertThat(context.regimeAssessmentId()).isNull();
    }
}
