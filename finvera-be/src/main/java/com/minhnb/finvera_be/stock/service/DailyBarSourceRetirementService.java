package com.minhnb.finvera_be.stock.service;

import com.minhnb.finvera_be.stock.repository.EquityDailyBarRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-0013 (Feature 021): after every daily-bar import, bars still attributed to a non-primary
 * source are retired wherever the primary source (VCI) now covers the same instrument and trading
 * date — otherwise two current bars per date would coexist and only the read-side source
 * preference would hide the stale one. Dates the primary source does not serve keep their old
 * bars (missing is never fabricated), and nothing is deleted (revision chain). Idempotent: the
 * second run retires zero rows.
 */
@Service
public class DailyBarSourceRetirementService {

    public static final String SOURCE_RETIRED = "SOURCE_RETIRED";
    private static final Logger log = LoggerFactory.getLogger(DailyBarSourceRetirementService.class);

    private final EquityDailyBarRepository bars;

    public DailyBarSourceRetirementService(EquityDailyBarRepository bars) {
        this.bars = bars;
    }

    @Transactional
    public int retireAllExcept(String primarySource) {
        int retired = bars.retireBarsNotFrom(primarySource);
        log.info("daily_bar_source_retirement primary={} retired_bars={}", primarySource, retired);
        return retired;
    }
}
