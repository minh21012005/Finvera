package com.minhnb.finvera_be.analyst.repository;

import com.minhnb.finvera_be.analyst.domain.ConversationExchangeStatus;
import com.minhnb.finvera_be.analyst.entity.AnalystConversationExchangeEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalystConversationExchangeRepository extends JpaRepository<AnalystConversationExchangeEntity, UUID> {
    Optional<AnalystConversationExchangeEntity> findByOwnerIdAndClientRequestId(UUID ownerId, UUID clientRequestId);
    Optional<AnalystConversationExchangeEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
    boolean existsByConversationIdAndOwnerIdAndStatus(UUID conversationId, UUID ownerId, ConversationExchangeStatus status);

    List<AnalystConversationExchangeEntity> findTop10ByConversationIdAndOwnerIdAndStatusAndSequenceNoLessThanOrderBySequenceNoDesc(
            UUID conversationId, UUID ownerId, ConversationExchangeStatus status, long sequenceNo);

    @Query("select e from AnalystConversationExchangeEntity e where e.conversationId=:conversationId "
            + "and e.ownerId=:ownerId order by e.sequenceNo desc, e.id desc")
    List<AnalystConversationExchangeEntity> findFirstPage(@Param("conversationId") UUID conversationId,
            @Param("ownerId") UUID ownerId, Pageable pageable);

    @Query("select e from AnalystConversationExchangeEntity e where e.conversationId=:conversationId "
            + "and e.ownerId=:ownerId and e.sequenceNo < :beforeSequence order by e.sequenceNo desc, e.id desc")
    List<AnalystConversationExchangeEntity> findPageBefore(@Param("conversationId") UUID conversationId,
            @Param("ownerId") UUID ownerId, @Param("beforeSequence") long beforeSequence, Pageable pageable);

    List<AnalystConversationExchangeEntity> findByConversationIdAndOwnerIdAndStatusAndCreatedAtBefore(
            UUID conversationId, UUID ownerId, ConversationExchangeStatus status, Instant cutoff, Pageable pageable);

    long countByConversationIdAndOwnerId(UUID conversationId, UUID ownerId);

    @Query("select e.conversationId, count(e), sum(case when e.status = com.minhnb.finvera_be.analyst.domain.ConversationExchangeStatus.PROCESSING then 1 else 0 end) "
            + "from AnalystConversationExchangeEntity e where e.ownerId=:ownerId and e.conversationId in :conversationIds group by e.conversationId")
    List<Object[]> summarize(@Param("ownerId") UUID ownerId, @Param("conversationIds") List<UUID> conversationIds);
}
