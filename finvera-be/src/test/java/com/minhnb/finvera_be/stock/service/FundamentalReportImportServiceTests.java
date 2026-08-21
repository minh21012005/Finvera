package com.minhnb.finvera_be.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minhnb.finvera_be.stock.service.FundamentalReportImportService.MetricPeriodRecord;
import com.minhnb.finvera_be.stock.service.FundamentalReportImportService.PackageInput;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IncomingFundamentalReport;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionResult;
import com.minhnb.finvera_be.stock.service.StockIngestionService.IngestionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FundamentalReportImportServiceTests {

    private final StockIngestionService ingestion = mock(StockIngestionService.class);
    private final FundamentalReportImportService service = new FundamentalReportImportService(ingestion);

    @Test
    void rejectsAnUnsupportedContractVersion() {
        PackageInput input = packageWith("wrong-contract",
                List.of(metric("NET_PROFIT", "QUARTER", 2026, 2, "1000000000")));
        assertThatThrownBy(() -> service.importPackage(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UNSUPPORTED_CONTRACT");
    }

    @Test
    void groupsRecordsForTheSamePeriodIntoOneIngestFundamentalReportCall() {
        when(ingestion.ingestFundamentalReport(any())).thenReturn(
                new IngestionResult(IngestionStatus.ACCEPTED, null, UUID.randomUUID(), 1));
        PackageInput input = packageWith("vnstock-fundamentals-v1", List.of(
                metric("NET_PROFIT", "QUARTER", 2026, 2, "1000000000"),
                metric("GROSS_PROFIT", "QUARTER", 2026, 2, "2000000000"),
                metric("NET_PROFIT", "QUARTER", 2025, 4, "900000000")));

        var summary = service.importPackage(input);

        assertThat(summary.results()).hasSize(2); // two distinct periods
        verify(ingestion, times(2)).ingestFundamentalReport(any());
        verify(ingestion).ingestFundamentalReport(org.mockito.ArgumentMatchers.argThat(
                (IncomingFundamentalReport report) -> report.fiscalYear() == 2026
                        && report.fiscalQuarter() == 2
                        && report.metrics().size() == 2
                        && report.source().equals("VNSTOCK_KBS")));
    }

    @Test
    void dropsAMetricCodeNotOnTheAllowlistRatherThanGuessingIt() {
        when(ingestion.ingestFundamentalReport(any())).thenReturn(
                new IngestionResult(IngestionStatus.ACCEPTED, null, UUID.randomUUID(), 1));
        PackageInput input = packageWith("vnstock-fundamentals-v1", List.of(
                metric("NET_PROFIT", "QUARTER", 2026, 2, "1000000000"),
                metric("SOME_UNMAPPED_RATIO", "QUARTER", 2026, 2, "42")));

        service.importPackage(input);

        verify(ingestion).ingestFundamentalReport(org.mockito.ArgumentMatchers.argThat(
                (IncomingFundamentalReport report) -> report.metrics().size() == 1
                        && report.metrics().get(0).metricCode().equals("NET_PROFIT")));
    }

    private static MetricPeriodRecord metric(
            String metricCode, String periodType, int year, Integer quarter, String value) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        return new MetricPeriodRecord(metricCode, periodType, year, quarter, start, end, new BigDecimal(value), "x");
    }

    private PackageInput packageWith(String contractVersion, List<MetricPeriodRecord> records) {
        String payload = "{\"records\":[]}";
        return new PackageInput(contractVersion, "finvera-vnstock-exporter", "0.1.0", "VNSTOCK_KBS", "VNM",
                "UNKNOWN", "UNKNOWN", "VND", 1, FundamentalReportImportService.sha256(payload), payload,
                Instant.parse("2026-01-16T00:00:00Z"), records);
    }
}
