package com.minhnb.finvera_be.market.repository;

import com.minhnb.finvera_be.market.entity.MarketIndexSnapshotEntity;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MarketIndexSnapshotRepository extends JpaRepository<MarketIndexSnapshotEntity, UUID> {

    Optional<MarketIndexSnapshotEntity> findFirstByIndexIdAndTradingDateOrderByObservedAtDescRevisionDesc(
            UUID indexId, LocalDate tradingDate);

    Optional<MarketIndexSnapshotEntity> findFirstByIndexIdAndTradingDateAndObservedAtOrderByRevisionDesc(
            UUID indexId, LocalDate tradingDate, Instant observedAt);

    Optional<MarketIndexSnapshotEntity> findFirstByIndexIdAndSourceNotAndTradingDateLessThanEqualOrderByTradingDateDescObservedAtDescRevisionDesc(
            UUID indexId, String excludedSource, LocalDate onOrBefore);

    @Query(value = """
            with ranked as (
                select snapshot.*, row_number() over (
                    partition by snapshot.trading_date
                    order by snapshot.observed_at desc, snapshot.revision desc
                ) as daily_rank
                from index_snapshot snapshot
                where snapshot.index_id = :indexId
                  and snapshot.source <> :excludedSource
                  and snapshot.trading_date <= :onOrBefore
            )
            select * from ranked where daily_rank = 1 order by trading_date asc
            """, nativeQuery = true)
    List<MarketIndexSnapshotEntity> findAcceptedDailyHistory(
            UUID indexId, String excludedSource, LocalDate onOrBefore);

    @Query(value = """
            with ranked as (
                select snapshot.*, row_number() over (
                    partition by snapshot.trading_date
                    order by snapshot.observed_at desc, snapshot.revision desc
                ) as daily_rank
                from index_snapshot snapshot
                where snapshot.index_id = :indexId
                  and snapshot.source like :sourcePrefix
                  and snapshot.session_state = 'CLOSED'
                  and snapshot.trading_date <= :onOrBefore
            )
            select * from ranked where daily_rank = 1 order by trading_date asc
            """, nativeQuery = true)
    List<MarketIndexSnapshotEntity> findAcceptedCompletedDailyHistory(
            UUID indexId, String sourcePrefix, LocalDate onOrBefore);
}
