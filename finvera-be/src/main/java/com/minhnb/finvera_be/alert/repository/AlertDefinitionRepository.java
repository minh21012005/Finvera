package com.minhnb.finvera_be.alert.repository;
import com.minhnb.finvera_be.alert.entity.AlertDefinitionEntity;import jakarta.persistence.LockModeType;import java.time.Instant;import java.util.*;import org.springframework.data.domain.*;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
public interface AlertDefinitionRepository extends JpaRepository<AlertDefinitionEntity,UUID>{
 Optional<AlertDefinitionEntity> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id,UUID ownerId);
 @Lock(LockModeType.PESSIMISTIC_WRITE)@Query("select a from AlertDefinitionEntity a where a.id=:id")Optional<AlertDefinitionEntity> findLockedById(@Param("id")UUID id);
 Page<AlertDefinitionEntity> findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(UUID ownerId,Pageable page);
 long countByOwnerIdAndDeletedAtIsNull(UUID ownerId);long countByOwnerIdAndEnabledTrueAndDeletedAtIsNull(UUID ownerId);
 @Query(value="select * from alert_definition where enabled=true and deleted_at is null and next_evaluation_at<=:now and (lease_until is null or lease_until<:now) order by next_evaluation_at,id limit :limit for update skip locked",nativeQuery=true)List<AlertDefinitionEntity> findAndLockDue(@Param("now")Instant now,@Param("limit")int limit);
 @Query("select count(a) from AlertDefinitionEntity a where a.enabled=true and a.deletedAt is null and a.nextEvaluationAt<=:now")long countDue(@Param("now")Instant now);
 @Query("select min(a.nextEvaluationAt) from AlertDefinitionEntity a where a.enabled=true and a.deletedAt is null and a.nextEvaluationAt<=:now")Instant oldestDueAt(@Param("now")Instant now);
 @Query(value="select * from alert_definition where enabled=true and deleted_at is null and lease_token is not null and lease_until<:now order by lease_until,id limit :limit for update skip locked",nativeQuery=true)List<AlertDefinitionEntity> findAndLockExpired(@Param("now")Instant now,@Param("limit")int limit);
}
