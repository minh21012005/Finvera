package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The published read-only interface other modules use for instrument
 * identity, trading calendar, and session-state facts already owned by the
 * {@code market} module (plan.md "Cross-module rule"). The {@code stock}
 * module MUST call only this interface and MUST NOT depend on
 * {@code market.entity} or {@code market.repository} directly; that
 * boundary is enforced by {@code StockModuleArchitectureTests}.
 */
public interface MarketReferenceDataService {

    /** The currently listed instrument for a symbol, if one is known and active. */
    Optional<InstrumentReference> findActiveInstrumentBySymbol(String symbol);

    /** Active instruments whose symbol starts with the given prefix, for symbol lookup (FR-016). */
    List<InstrumentReference> searchActiveInstrumentsBySymbolPrefix(String symbolPrefix, int limit);

    /** The trading-day and session-window state at an instant, for one venue. */
    SessionContext resolveSession(String venue, Instant at);

    record InstrumentReference(
            UUID instrumentId,
            String venue,
            String symbol,
            String instrumentType,
            String status) {
    }

    record SessionContext(SessionState state, LocalDate tradingDate) {
    }
}
