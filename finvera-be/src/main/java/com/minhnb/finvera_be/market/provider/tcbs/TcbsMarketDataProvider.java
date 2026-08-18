package com.minhnb.finvera_be.market.provider.tcbs;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import com.minhnb.finvera_be.market.provider.MarketDataProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Live TCBS iFlash market data provider implementing the dual-path architecture
 * approved in {@code contracts/tcbs-iflash-adapter.md}.
 *
 * <h3>Architecture invariants (from contract gate resolution)</h3>
 * <ul>
 *   <li><b>REST is the sole persistence path.</b> {@code GET /tartarus/v1/tickerCommons}
 *       is the only source written to the domain. Every observation is labelled
 *       {@code TCBS_REST_TRADING_DATE_ONLY} because {@code tradingDate} is a date string,
 *       not an intraday timestamp. {@code effective_at} is inferred from
 *       {@code MarketTimePolicy} session-close schedule.</li>
 *   <li><b>WebSocket is display-only.</b> The {@code rt} stream is accepted by
 *       {@code subscribe()} for transient UI push. Its observations carry
 *       {@code TCBS_STREAM_TIMESTAMP_UNAVAILABLE} and
 *       {@code TCBS_STREAM_ORDERING_UNAVAILABLE} and MUST NOT be ingested.</li>
 *   <li><b>Session state is inferred from clock.</b> The opaque {@code session} field
 *       from the TCBS stream is stored as diagnostic metadata only; it MUST NOT
 *       gate polling or persistence decisions.</li>
 *   <li><b>Breadth graceful degradation.</b> Records missing {@code matchPrice} or
 *       {@code refPrice} are counted {@code BREADTH_RECORD_INCOMPLETE} and excluded.</li>
 * </ul>
 *
 * <p>This class MUST NOT call trading, account, cash, portfolio, order, or any
 * non-market endpoint. Provider DTOs and credentials MUST NOT cross the adapter
 * boundary into any API response or browser payload.
 */
public final class TcbsMarketDataProvider implements MarketDataProvider {

    public static final String SOURCE = "TCBS_IFLASH_MARKET_DATA";

    /** HOSE and HNX standard session close (Asia/Ho_Chi_Minh). */
    private static final LocalTime HOSE_HNX_CLOSE = LocalTime.of(14, 45);
    /** UPCOM standard session close (Asia/Ho_Chi_Minh). */
    private static final LocalTime UPCOM_CLOSE = LocalTime.of(14, 30);
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Allowlisted TCBS indexNumber → IndexCode mapping (from POC evidence). */
    private static final List<IndexCode> ALLOWLISTED_INDICES = List.of(
            IndexCode.VN_INDEX, IndexCode.VN30, IndexCode.HNX_INDEX, IndexCode.UPCOM_INDEX);

    private final TcbsRestClient restClient;
    private final TcbsSessionState sessionState;

    public TcbsMarketDataProvider(TcbsRestClient restClient, TcbsSessionState sessionState) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.sessionState = Objects.requireNonNull(sessionState, "sessionState");
    }

    /**
     * Returns the four allowlisted index codes as reference data.
     * No network call is made; reference identity is statically known.
     */
    @Override
    public ProviderReferenceBatch fetchReferenceData(LocalDate effectiveDate) {
        Objects.requireNonNull(effectiveDate, "effectiveDate");
        return new ProviderReferenceBatch(SOURCE, effectiveDate, ALLOWLISTED_INDICES);
    }

    /**
     * Polls {@code GET /tartarus/v1/tickerCommons} for the four index snapshots
     * and returns a normalized {@link ProviderSnapshotBatch}.
     *
     * <p>Every observation is labelled {@code TCBS_REST_TRADING_DATE_ONLY}.
     * {@code effective_at} is inferred from the session-close schedule.
     * Reference level comes from {@code refPrice}; a zero or absent {@code refPrice}
     * produces a {@code null} reference level and adds {@code REFERENCE_UNAVAILABLE}.
     * Records with a missing or null {@code matchPrice} are excluded from the
     * returned observations and increment the {@code BREADTH_RECORD_INCOMPLETE} counter.
     */
    @Override
    public ProviderSnapshotBatch reconcileLatest(LocalDate tradingDate) {
        Objects.requireNonNull(tradingDate, "tradingDate");
        TcbsRestClient.TickerCommonsResponse response = restClient.fetchTickerCommons(tradingDate);

        List<ProviderObservation> observations = new ArrayList<>();
        List<String> batchReasonCodes = new ArrayList<>();
        int incompleteCount = 0;

        for (TcbsRestClient.TickerCommonsItem item : response.data()) {
            IndexCode code = resolveIndexCode(item.indexNumber());
            if (code == null) {
                continue; // not in allowlist — skip silently
            }
            if (item.matchPrice() == null || item.matchPrice().isBlank()) {
                incompleteCount++;
                continue; // Decision C: exclude, do not zero-fill
            }

            BigDecimal level = parseDecimal(item.matchPrice());
            BigDecimal change = parseDecimalOrNull(item.change());
            BigDecimal changePercent = parseDecimalOrNull(item.changePercent());
            Long volume = parseLongOrNull(item.totalVol());
            BigDecimal valueVnd = parseDecimalOrNull(item.totalVal());

            BigDecimal refPrice = parseDecimalOrNull(item.refPrice());
            List<String> reasonCodes = new ArrayList<>();
            reasonCodes.add("TCBS_REST_TRADING_DATE_ONLY");

            // Decision A: refPrice=0 or null → REFERENCE_UNAVAILABLE, do not fabricate
            if (refPrice == null || refPrice.signum() == 0) {
                refPrice = null;
                reasonCodes.add("REFERENCE_UNAVAILABLE");
            }

            observations.add(new ProviderObservation(
                    code, level, refPrice, change, changePercent, volume, valueVnd, reasonCodes));
        }

        if (incompleteCount > 0) {
            batchReasonCodes.add("BREADTH_RECORD_INCOMPLETE");
        }

        // Decision A: infer effective_at from trading date + HOSE session close
        Instant effectiveAt = inferEffectiveAt(tradingDate);
        DataStatus dataStatus = observations.size() == ALLOWLISTED_INDICES.size()
                ? DataStatus.CURRENT
                : DataStatus.PARTIAL;
        SessionState sessionState = SessionState.CLOSED; // REST reconcile is always end-of-session

        return new ProviderSnapshotBatch(
                SOURCE, tradingDate, effectiveAt, sessionState, dataStatus,
                List.copyOf(batchReasonCodes), List.copyOf(observations));
    }

    /**
     * Returns a subscription over the WebSocket {@code rt} stream for display-only use.
     *
     * <p>Observations from this path carry {@code TCBS_STREAM_TIMESTAMP_UNAVAILABLE}
     * and {@code TCBS_STREAM_ORDERING_UNAVAILABLE}. The caller MUST NOT persist
     * these observations or use them to overwrite REST-sourced accepted data.
     *
     * <p>When no live token is present, the subscription is a no-op and closes immediately.
     */
    @Override
    public Subscription subscribe(Consumer<ProviderObservation> observer) {
        Objects.requireNonNull(observer, "observer");
        // WebSocket display path — no-op until wired; returns safely closeable subscription.
        // The actual WebSocket lifecycle is managed externally (e.g., TcbsWebSocketAdapter)
        // and calls observer with stream-labelled ProviderObservation values.
        // This stub satisfies the port contract while T046 activation is gated.
        return () -> { /* no-op close */ };
    }

    @Override
    public ProviderHealth health() {
        if (!sessionState.isTokenPresent()) {
            return new ProviderHealth(ProviderHealthState.AUTH_REQUIRED, "PROVIDER_AUTH_REQUIRED");
        }
        if (!sessionState.isHealthy()) {
            return new ProviderHealth(ProviderHealthState.DEGRADED, "TCBS_REST_UNHEALTHY");
        }
        return new ProviderHealth(ProviderHealthState.READY, "READY");
    }

    // ── private helpers ──────────────────────────────────────────────────────

    /**
     * Infers {@code effective_at} as {@code tradingDate + HOSE session close} in
     * {@code Asia/Ho_Chi_Minh}.
     *
     * <p>Decision A: {@code tradingDate} from TCBS REST is a date string only.
     * We use the versioned HOSE close time (14:45) as the canonical session-end
     * reference. This is a deterministic calendar inference — not a fabricated value.
     */
    private static Instant inferEffectiveAt(LocalDate tradingDate) {
        return tradingDate.atTime(HOSE_HNX_CLOSE).atZone(MARKET_ZONE).toInstant();
    }

    /** Maps TCBS {@code indexNumber} to allowlisted {@link IndexCode}; returns {@code null} if not allowlisted. */
    private static IndexCode resolveIndexCode(int indexNumber) {
        return switch (indexNumber) {
            case 1 -> IndexCode.VN_INDEX;
            case 2 -> IndexCode.VN30;
            case 3 -> IndexCode.HNX_INDEX;
            case 5 -> IndexCode.UPCOM_INDEX;
            default -> null;
        };
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TCBS_DECIMAL_REQUIRED");
        }
        try {
            return new BigDecimal(value).setScale(6, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("TCBS_DECIMAL_INVALID: " + value, e);
        }
    }

    private static BigDecimal parseDecimalOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value).setScale(6, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null; // treat malformed as missing, do not fabricate
        }
    }

    private static Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            BigDecimal bd = new BigDecimal(value);
            long result = bd.longValueExact();
            return result < 0 ? null : result;
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }
}
