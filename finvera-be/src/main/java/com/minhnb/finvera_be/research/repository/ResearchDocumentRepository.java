package com.minhnb.finvera_be.research.repository;

import com.minhnb.finvera_be.research.domain.DocumentType;
import com.minhnb.finvera_be.research.entity.ResearchDocumentEntity;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ResearchDocumentRepository extends JpaRepository<ResearchDocumentEntity, UUID> {

    Optional<ResearchDocumentEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<ResearchDocumentEntity> findByOwnerIdAndIdempotencyKey(UUID ownerId, String idempotencyKey);

    @Query("""
            SELECT d FROM ResearchDocumentEntity d
            WHERE d.ownerId = :ownerId
              AND (:symbolId IS NULL OR d.symbolId = :symbolId)
              AND (:documentType IS NULL OR d.documentType = :documentType)
              AND (CAST(:dateFrom AS date) IS NULL OR d.publicationDate >= :dateFrom)
              AND (CAST(:dateTo AS date) IS NULL OR d.publicationDate <= :dateTo)
            ORDER BY d.submittedAt DESC
            """)
    Page<ResearchDocumentEntity> searchDocuments(
            @Param("ownerId") UUID ownerId,
            @Param("symbolId") UUID symbolId,
            @Param("documentType") DocumentType documentType,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            Pageable pageable);

    @Query("""
            SELECT d FROM ResearchDocumentEntity d WHERE d.ownerId=:ownerId
              AND d.ingestionStatus=com.minhnb.finvera_be.research.domain.IngestionStatus.READY
              AND (d.processedAt>:after OR (d.processedAt=:after AND :afterId IS NOT NULL AND d.id>:afterId))
              AND (:symbolId IS NULL OR d.symbolId=:symbolId)
              AND (:documentType IS NULL OR d.documentType=:documentType)
            ORDER BY d.processedAt ASC,d.id ASC
            """)
    List<ResearchDocumentEntity> findAcceptedAfter(@Param("ownerId")UUID ownerId,@Param("symbolId")UUID symbolId,
            @Param("documentType")DocumentType documentType,@Param("after")Instant after,
            @Param("afterId")UUID afterId,Pageable pageable);
}
