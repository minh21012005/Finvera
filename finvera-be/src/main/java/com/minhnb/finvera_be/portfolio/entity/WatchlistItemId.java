package com.minhnb.finvera_be.portfolio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class WatchlistItemId implements Serializable {

    @Column(name = "watchlist_id", nullable = false)
    private UUID watchlistId;

    @Column(name = "instrument_id", nullable = false)
    private UUID instrumentId;

    public WatchlistItemId() {
    }

    public WatchlistItemId(UUID watchlistId, UUID instrumentId) {
        this.watchlistId = watchlistId;
        this.instrumentId = instrumentId;
    }

    public UUID getWatchlistId() {
        return watchlistId;
    }

    public UUID getInstrumentId() {
        return instrumentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WatchlistItemId that = (WatchlistItemId) o;
        return Objects.equals(watchlistId, that.watchlistId) && Objects.equals(instrumentId, that.instrumentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(watchlistId, instrumentId);
    }
}
