package com.minhnb.finvera_be.stock.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.InstrumentReference;
import com.minhnb.finvera_be.market.service.MarketReferenceDataService.SessionContext;
import com.minhnb.finvera_be.stock.entity.EquityDailyBarEntity;
import com.minhnb.finvera_be.stock.entity.EquityProfileEntity;
import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import com.minhnb.finvera_be.stock.repository.EquityProfileRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalMetricCatalogRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalReportMetricRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalReportRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalSummaryInputRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalSummaryMetricRepository;
import com.minhnb.finvera_be.stock.repository.FundamentalSummaryRepository;
import com.minhnb.finvera_be.stock.repository.SectorReferenceRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorResultRepository;
import com.minhnb.finvera_be.stock.repository.TechnicalIndicatorValueRepository;
import com.minhnb.finvera_be.stock.repository.ValuationAssessmentInputRepository;
import com.minhnb.finvera_be.stock.repository.ValuationAssessmentRepository;
import com.minhnb.finvera_be.stock.repository.ValuationMetricRepository;
import com.minhnb.finvera_be.stock.service.FundamentalReportService;
import com.minhnb.finvera_be.stock.service.StockChartService;
import com.minhnb.finvera_be.stock.service.StockIngestionService;
import com.minhnb.finvera_be.stock.service.StockOverviewService;
import com.minhnb.finvera_be.stock.service.TechnicalIndicatorService;
import com.minhnb.finvera_be.stock.service.ValuationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * T066 [NFR-001, NFR-002, NFR-003, SC-005]
 * Performance and coherence test suite for stock detail read services.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockDetailPerformanceTests {

    private static final Duration API_P95_TARGET = Duration.ofMillis(500);
    private static final String SYMBOL = "FPT";
    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-17T03:00:15Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock private MarketReferenceDataService referenceData;
    @Mock private EquityProfileRepository profiles;
    @Mock private EquityDailyBarRepository dailyBars;
    @Mock private SectorReferenceRepository sectors;
    @Mock private TechnicalIndicatorResultRepository technicalResults;
    @Mock private TechnicalIndicatorValueRepository technicalValues;
    @Mock private StockIngestionService ingestion;
    @Mock private FundamentalReportRepository reports;
    @Mock private FundamentalReportMetricRepository reportMetrics;
    @Mock private FundamentalSummaryRepository summaries;
    @Mock private FundamentalSummaryMetricRepository summaryMetrics;
    @Mock private FundamentalSummaryInputRepository summaryInputs;
    @Mock private FundamentalMetricCatalogRepository catalog;
    @Mock private ValuationAssessmentRepository assessments;
    @Mock private ValuationMetricRepository valuationMetrics;
    @Mock private ValuationAssessmentInputRepository assessmentInputs;

    private StockOverviewService overviewService;
    private StockChartService chartService;
    private TechnicalIndicatorService technicalService;
    private FundamentalReportService fundamentalService;
    private ValuationService valuationService;

    private InstrumentReference instrument;
    private EquityProfileEntity profile;
    private List<EquityDailyBarEntity> bars;

    @BeforeEach
    void setUp() {
        instrument = new InstrumentReference(INSTRUMENT_ID, "HOSE", SYMBOL, "STOCK", "LISTED");

        profile = new EquityProfileEntity(
                UUID.randomUUID(), INSTRUMENT_ID, "CTCP FPT", "FPT Corp",
                null, 1460000000L, new BigDecimal("0.850000"), "LISTED",
                LocalDate.of(2026, 1, 1), null, "FINVERA_FIXTURE", "1", "NONE"
        );

        bars = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            LocalDate d = LocalDate.of(2025, 8, 1).plusDays(i);
            bars.add(new EquityDailyBarEntity(
                    UUID.randomUUID(), INSTRUMENT_ID, UUID.randomUUID(), null,
                    d, new BigDecimal("130000.00"), new BigDecimal("132000.00"),
                    new BigDecimal("129000.00"), new BigDecimal("131000.00"),
                    new BigDecimal("131000.00"), new BigDecimal("1.00"),
                    "ADJUSTED", 5000000L, new BigDecimal("655000000000.00"),
                    "FINVERA_FIXTURE", NOW, NOW, 1, true, null, "NONE"
            ));
        }

        overviewService = new StockOverviewService(referenceData, profiles, dailyBars, sectors, FIXED_CLOCK);
        chartService = new StockChartService(referenceData, dailyBars, FIXED_CLOCK);
        technicalService = new TechnicalIndicatorService(referenceData, dailyBars, technicalResults, technicalValues, ingestion, FIXED_CLOCK);
        fundamentalService = new FundamentalReportService(referenceData, reports, reportMetrics, summaries, summaryMetrics, summaryInputs, catalog, FIXED_CLOCK);
        valuationService = new ValuationService(referenceData, dailyBars, profiles, reports, reportMetrics, sectors, assessments, valuationMetrics, assessmentInputs, fundamentalService, ingestion, FIXED_CLOCK, false);

        when(referenceData.findActiveInstrumentBySymbol(SYMBOL)).thenReturn(Optional.of(instrument));
        when(referenceData.resolveSession(any(), any())).thenReturn(new SessionContext(SessionState.OPEN, LocalDate.of(2026, 8, 17)));
        when(profiles.findFirstByInstrumentIdAndEffectiveToIsNull(INSTRUMENT_ID))
                .thenReturn(Optional.of(profile));
        when(dailyBars.findByInstrumentIdAndCurrentTrueOrderByTradingDateAsc(INSTRUMENT_ID))
                .thenReturn(bars);
        when(dailyBars.findByInstrumentIdAndCurrentTrueAndTradingDateBetweenOrderByTradingDateAsc(eq(INSTRUMENT_ID), any(), any()))
                .thenReturn(bars);
        when(reports.findAllByInstrumentIdAndCurrentTrueOrderByPeriodEndDesc(INSTRUMENT_ID))
                .thenReturn(List.of());
    }

    @Test
    void eachSectionReadHasFixtureP95AtOrBelowFiveHundredMilliseconds() {
        // Warmup
        overviewService.findBySymbol(SYMBOL);
        chartService.findBySymbol(SYMBOL, "1Y");
        technicalService.findBySymbol(SYMBOL);
        fundamentalService.findBySymbol(SYMBOL);
        valuationService.findBySymbol(SYMBOL);

        long[] samples = new long[30];
        for (int i = 0; i < samples.length; i++) {
            long start = System.nanoTime();
            var ov = overviewService.findBySymbol(SYMBOL);
            var ch = chartService.findBySymbol(SYMBOL, "1Y");
            var tc = technicalService.findBySymbol(SYMBOL);
            var fn = fundamentalService.findBySymbol(SYMBOL);
            var vl = valuationService.findBySymbol(SYMBOL);
            samples[i] = System.nanoTime() - start;

            assertThat(ov).isPresent();
            assertThat(ch).isPresent();
            assertThat(tc).isPresent();
            assertThat(fn).isPresent();
            assertThat(vl).isPresent();
        }

        Arrays.sort(samples);
        long p95 = samples[((samples.length * 95 + 99) / 100) - 1];
        assertThat(Duration.ofNanos(p95)).isLessThanOrEqualTo(API_P95_TARGET);
    }

    @Test
    void coherenceKeyDetectsChangeWhenContributingRevisionChanges() {
        var firstValuation = valuationService.findBySymbol(SYMBOL);
        assertThat(firstValuation).isPresent();
        String key1 = firstValuation.get().coherenceKey();

        // Mutate revision on latest daily bar
        List<EquityDailyBarEntity> updatedBars = new ArrayList<>(bars);
        EquityDailyBarEntity last = updatedBars.getLast();
        EquityDailyBarEntity mutatedLast = new EquityDailyBarEntity(
                last.getId(), last.getInstrumentId(), last.getIngestionRecordId(), last.getImportBatchId(),
                last.getTradingDate(), last.getOpenPrice(), last.getHighPrice(), last.getLowPrice(),
                last.getClosePrice(), last.getAdjustedClose(), last.getAdjustmentFactor(),
                last.getAdjustmentStatus(), last.getVolume(), last.getValueVnd(),
                last.getSource(), last.getObservedAt(), last.getAcceptedAt(), 2,
                last.isCurrent(), last.getSupersedesId(), last.getQualityReason()
        );
        updatedBars.set(updatedBars.size() - 1, mutatedLast);

        when(dailyBars.findByInstrumentIdAndCurrentTrueOrderByTradingDateAsc(INSTRUMENT_ID))
                .thenReturn(updatedBars);

        var secondValuation = valuationService.findBySymbol(SYMBOL);
        assertThat(secondValuation).isPresent();
        String key2 = secondValuation.get().coherenceKey();

        assertThat(key1).isNotNull();
        assertThat(key2).isNotNull();
        assertThat(key1).isNotEqualTo(key2);
    }
}
