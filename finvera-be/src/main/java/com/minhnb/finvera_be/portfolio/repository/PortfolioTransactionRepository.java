package com.minhnb.finvera_be.portfolio.repository;

import com.minhnb.finvera_be.portfolio.entity.PortfolioTransactionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransactionEntity, UUID> {

    List<PortfolioTransactionEntity> findByPortfolioIdOrderByExecutedAtAscSequenceNoAsc(UUID portfolioId);

    List<PortfolioTransactionEntity> findByPortfolioIdOrderBySequenceNoAsc(UUID portfolioId);

    Optional<PortfolioTransactionEntity> findByPortfolioIdAndIdempotencyKey(UUID portfolioId, String idempotencyKey);

    boolean existsByPortfolioIdAndIdempotencyKey(UUID portfolioId, String idempotencyKey);

    boolean existsByVoidsTransactionId(UUID voidsTransactionId);

    Optional<PortfolioTransactionEntity> findByIdAndPortfolioId(UUID id, UUID portfolioId);
}
