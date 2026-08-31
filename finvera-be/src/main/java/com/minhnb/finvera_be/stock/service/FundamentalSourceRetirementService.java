package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.stock.entity.FundamentalReportEntity;
import com.minhnb.finvera_be.stock.repository.FundamentalReportRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feature 018 (ADR-0011, Q-57): retires every current {@code fundamental_report} row still
 * attributed to a source whose period labels proved wrong (KBS statement pages). Contract
 * vci-fundamentals-v1 rule I-1 replaces KBS rows period by period as VCI rows land; rows for
 * periods VCI does not serve (e.g. A32 has no quarterly statements) would otherwise stay current
 * with the wrong period attached. Retirement marks them {@code current = false} with reason
 * {@code SOURCE_RETIRED}; nothing is deleted (revision chain, DATA-006). Downstream, the
 * fundamentals summary recomputes on the next read (contributing set changed) and the valuation
 * warmup must be forced once ({@code -ForceWarmup}) because retirement adds no new
 * {@code accepted_at}.
 */
@Service
public class FundamentalSourceRetirementService {

    public static final String SOURCE_RETIRED = "SOURCE_RETIRED";
    private static final Logger log = LoggerFactory.getLogger(FundamentalSourceRetirementService.class);

    private final FundamentalReportRepository reports;

    public FundamentalSourceRetirementService(FundamentalReportRepository reports) {
        this.reports = reports;
    }

    @Transactional
    public Summary retire(String source) {
        List<FundamentalReportEntity> current = reports.findAllBySourceAndCurrentTrue(source);
        long instruments = current.stream().map(FundamentalReportEntity::getInstrumentId).distinct().count();
        for (FundamentalReportEntity report : current) {
            report.markRetired(SOURCE_RETIRED);
        }
        reports.saveAll(current);
        Summary summary = new Summary(source, current.size(), instruments);
        log.info("fundamental_source_retirement source={} retired_reports={} instruments={}",
                summary.source(), summary.retiredReports(), summary.instruments());
        return summary;
    }

    /**
     * Default, idempotent form run at the end of every fundamentals import: retire every current row
     * that did not come from the primary fundamentals source. First run after the VCI switch retires
     * the KBS rows VCI did not replace (annual-only symbols); every later run retires nothing.
     */
    @Transactional
    public Summary retireAllExcept(String primarySource) {
        List<FundamentalReportEntity> stale = reports.findAllByCurrentTrueAndSourceNot(primarySource);
        long instruments = stale.stream().map(FundamentalReportEntity::getInstrumentId).distinct().count();
        for (FundamentalReportEntity report : stale) {
            report.markRetired(SOURCE_RETIRED);
        }
        reports.saveAll(stale);
        Summary summary = new Summary("!" + primarySource, stale.size(), instruments);
        log.info("fundamental_source_retirement source={} primary={} retired_reports={} instruments={}",
                summary.source(), primarySource, summary.retiredReports(), summary.instruments());
        return summary;
    }

    public record Summary(String source, int retiredReports, long instruments) {
    }
}
