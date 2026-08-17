package com.minhnb.finvera_be.market.repository;

import com.minhnb.finvera_be.market.entity.MarketIndexSnapshotEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Read-only projection for one accepted market trading-date boundary. */
public interface MarketOverviewRepository extends Repository<MarketIndexSnapshotEntity, java.util.UUID> {

    @Query(value = """
            with latest_date as (
                select max(trading_date) as trading_date from index_snapshot
            ), ranked as (
                select mi.code as index_code, mi.display_name as display_name, mi.venue as venue,
                       snapshot.trading_date, snapshot.observed_at, snapshot.session_state,
                       snapshot.index_level, snapshot.reference_level, snapshot.matched_volume,
                       snapshot.matched_value_vnd, snapshot.source, snapshot.revision,
                       row_number() over (
                           partition by snapshot.index_id
                           order by snapshot.observed_at desc, snapshot.revision desc
                       ) as rank
                from index_snapshot snapshot
                join market_index mi on mi.id = snapshot.index_id
                join latest_date latest on latest.trading_date = snapshot.trading_date
            )
            select index_code as indexCode, display_name as displayName, venue,
                   trading_date as tradingDate, observed_at as observedAt, session_state as sessionState,
                   index_level as indexLevel, reference_level as referenceLevel,
                   matched_volume as matchedVolume, matched_value_vnd as matchedValueVnd,
                   source, revision
            from ranked
            where rank = 1
            order by case index_code
                when 'VN_INDEX' then 1 when 'VN30' then 2 when 'HNX_INDEX' then 3 else 4 end
            """, nativeQuery = true)
    List<LatestIndexSnapshot> findLatestAcceptedIndexBoundary();

    interface LatestIndexSnapshot {
        String getIndexCode();
        String getDisplayName();
        String getVenue();
        LocalDate getTradingDate();
        Instant getObservedAt();
        String getSessionState();
        BigDecimal getIndexLevel();
        BigDecimal getReferenceLevel();
        Long getMatchedVolume();
        BigDecimal getMatchedValueVnd();
        String getSource();
        Integer getRevision();
    }
}
