package com.minhnb.finvera_be.analyst.repository;

import com.minhnb.finvera_be.analyst.entity.AnalystConversationEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalystConversationRepository extends JpaRepository<AnalystConversationEntity, UUID> {
    Optional<AnalystConversationEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AnalystConversationEntity c where c.id=:id and c.ownerId=:ownerId")
    Optional<AnalystConversationEntity> findOwnedForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    @Query("select c from AnalystConversationEntity c where c.ownerId=:ownerId "
            + "order by c.lastActivityAt desc, c.id desc")
    List<AnalystConversationEntity> findFirstPage(@Param("ownerId") UUID ownerId, Pageable pageable);

    @Query("select c from AnalystConversationEntity c where c.ownerId=:ownerId and "
            + "(c.lastActivityAt < :beforeAt or (c.lastActivityAt = :beforeAt and c.id < :beforeId)) "
            + "order by c.lastActivityAt desc, c.id desc")
    List<AnalystConversationEntity> findPageBefore(@Param("ownerId") UUID ownerId,
            @Param("beforeAt") Instant beforeAt, @Param("beforeId") UUID beforeId, Pageable pageable);
}
