package com.minhnb.finvera_be.stock.domain.time;

import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.CURRENT;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.DELAYED;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.STALE;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.UNAVAILABLE;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Per-dataset freshness thresholds from research.md R-010. Each dataset has
 * its own boundary because a two-hour-old fundamental report is normal while
 * a two-hour-old quote is not, and the reverse comparison is equally wrong.
 */
public final class StockFreshnessPolicy {

    private static final Duration QUOTE_OPEN_CURRENT_GRACE = Duration.ofSeconds(30);
    private static final Duration QUOTE_OPEN_STALE_THRESHOLD = Duration.ofMinutes(15);
    private static final long FUNDAMENTALS_CURRENT_MAX_AGE_DAYS = 190;
    private static final long FUNDAMENTALS_STALE_THRESHOLD_DAYS = 280;

    /** Live quote freshness while the session is open, measured past the contracted delay. */
    public DataStatus evaluateQuoteWhileOpen(Instant observedAt, Instant evaluatedAt, Duration contractedDelay) {
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Objects.requireNonNull(contractedDelay, "contractedDelay");
        if (contractedDelay.isNegative()) {
            throw new IllegalArgumentException("contractedDelay must not be negative");
        }
        if (evaluatedAt.isBefore(observedAt)) {
            throw new IllegalArgumentException("evaluatedAt must not precede observedAt");
        }
        Duration ageAfterContractedDelay = Duration.between(observedAt.plus(contractedDelay), evaluatedAt);
        if (ageAfterContractedDelay.isNegative() || ageAfterContractedDelay.compareTo(QUOTE_OPEN_CURRENT_GRACE) <= 0) {
            return CURRENT;
        }
        if (ageAfterContractedDelay.compareTo(QUOTE_OPEN_STALE_THRESHOLD) <= 0) {
            return DELAYED;
        }
        return STALE;
    }

    /** Live quote freshness while the session is closed, measured by completed sessions behind. */
    public DataStatus evaluateQuoteWhileClosed(int completedSessionsBehind) {
        if (completedSessionsBehind < 0) {
            throw new IllegalArgumentException("completedSessionsBehind must not be negative");
        }
        return sessionsBehindToStatus(completedSessionsBehind);
    }

    /** Daily bar series freshness, measured by trailing sessions missing at the series end. */
    public DataStatus evaluateDailyBarSeries(int missingTrailingSessions) {
        if (missingTrailingSessions < 0) {
            throw new IllegalArgumentException("missingTrailingSessions must not be negative");
        }
        return sessionsBehindToStatus(missingTrailingSessions);
    }

    /** Fundamental report freshness, measured by age of the reporting period end. */
    public DataStatus evaluateFundamentalReport(LocalDate periodEnd, LocalDate evaluationDate) {
        Objects.requireNonNull(periodEnd, "periodEnd");
        Objects.requireNonNull(evaluationDate, "evaluationDate");
        if (evaluationDate.isBefore(periodEnd)) {
            throw new IllegalArgumentException("evaluationDate must not precede periodEnd");
        }
        long ageDays = ChronoUnit.DAYS.between(periodEnd, evaluationDate);
        if (ageDays <= FUNDAMENTALS_CURRENT_MAX_AGE_DAYS) {
            return CURRENT;
        }
        if (ageDays <= FUNDAMENTALS_STALE_THRESHOLD_DAYS) {
            return DELAYED;
        }
        return STALE;
    }

    /** Valuation freshness inherits the most actionable (worst) state among its contributing inputs. */
    public DataStatus evaluateValuationAssessment(List<DataStatus> inputStatuses) {
        Objects.requireNonNull(inputStatuses, "inputStatuses");
        if (inputStatuses.isEmpty()) {
            return UNAVAILABLE;
        }
        return inputStatuses.stream().reduce(DataStatus::mostActionable).orElse(UNAVAILABLE);
    }

    public DataStatus evaluateMissing() {
        return UNAVAILABLE;
    }

    private static DataStatus sessionsBehindToStatus(int sessionsBehind) {
        if (sessionsBehind == 0) {
            return CURRENT;
        }
        if (sessionsBehind == 1) {
            return DELAYED;
        }
        return STALE;
    }
}
