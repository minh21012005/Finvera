package com.minhnb.finvera_be.research.repository;

import com.minhnb.finvera_be.research.entity.ResearchChunkEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResearchChunkRepository extends JpaRepository<ResearchChunkEntity, UUID> {

    List<ResearchChunkEntity> findByResearchDocumentIdOrderByChunkIndexAsc(UUID researchDocumentId);

    List<ResearchChunkEntity> findByNewsArticleIdOrderByChunkIndexAsc(UUID newsArticleId);

    List<ResearchChunkEntity> findByIdInAndOwnerId(Collection<UUID> ids, UUID ownerId);

    Optional<ResearchChunkEntity> findByVectorPointId(UUID vectorPointId);

    void deleteByResearchDocumentId(UUID researchDocumentId);

    void deleteByNewsArticleId(UUID newsArticleId);
}
