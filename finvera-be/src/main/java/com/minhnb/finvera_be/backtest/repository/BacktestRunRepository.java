package com.minhnb.finvera_be.backtest.repository;

import com.minhnb.finvera_be.backtest.domain.BacktestTypes.RunStatus;
import com.minhnb.finvera_be.backtest.entity.BacktestRunEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BacktestRunRepository extends JpaRepository<BacktestRunEntity,UUID> {
    Optional<BacktestRunEntity> findByIdAndOwnerId(UUID id,UUID ownerId);
    Optional<BacktestRunEntity> findByOwnerIdAndIdempotencyKey(UUID ownerId,String idempotencyKey);
    Page<BacktestRunEntity> findAllByOwnerIdOrderByCreatedAtDescIdDesc(UUID ownerId,Pageable pageable);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query(value="delete from backtest_run where id=:id and owner_id=:ownerId",nativeQuery=true)
    int deleteByIdAndOwnerId(@Param("id") UUID id,@Param("ownerId") UUID ownerId);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query(value="delete from backtest_run where owner_id=:ownerId",nativeQuery=true)
    int deleteAllByOwnerId(@Param("ownerId") UUID ownerId);

    @Query("select r.id from BacktestRunEntity r where r.status='QUEUED' order by r.createdAt,r.id")
    List<UUID> findQueuedIds(Pageable pageable);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update BacktestRunEntity r set r.status='RUNNING',r.attemptCount=r.attemptCount+1,r.startedAt=coalesce(r.startedAt,:now),r.heartbeatAt=:now where r.id=:id and r.status='QUEUED' and r.attemptCount<2")
    int claim(@Param("id") UUID id,@Param("now") Instant now);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query(value="update backtest_run set heartbeat_at=:now where id=:id and status='RUNNING'",nativeQuery=true)
    int heartbeat(@Param("id") UUID id,@Param("now") Instant now);

    @Query("select r.id from BacktestRunEntity r where r.status='RUNNING' and r.heartbeatAt<:cutoff order by r.heartbeatAt,r.id")
    List<UUID> findStaleIds(@Param("cutoff") Instant cutoff,Pageable pageable);

    @Query("select min(r.createdAt) from BacktestRunEntity r where r.status='QUEUED'")
    Instant findOldestQueuedCreatedAt();

    long countByStatus(RunStatus status);
}
