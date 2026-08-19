package com.minhnb.finvera_be.portfolio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "watchlist_item")
public class WatchlistItemEntity {

    @EmbeddedId
    private WatchlistItemId id;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected WatchlistItemEntity() {
    }

    public WatchlistItemEntity(WatchlistItemId id, Instant addedAt) {
        this.id = id;
        this.addedAt = addedAt;
    }

    public WatchlistItemEntity(UUID watchlistId, UUID instrumentId, Instant addedAt) {
        this(new WatchlistItemId(watchlistId, instrumentId), addedAt);
    }

    public WatchlistItemId getId() {
        return id;
    }

    public UUID getWatchlistId() {
        return id != null ? id.getWatchlistId() : null;
    }

    public UUID getInstrumentId() {
        return id != null ? id.getInstrumentId() : null;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
