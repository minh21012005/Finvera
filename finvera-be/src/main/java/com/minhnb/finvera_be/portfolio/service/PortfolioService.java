package com.minhnb.finvera_be.portfolio.service;

import com.minhnb.finvera_be.portfolio.dto.CreatePortfolioRequest;
import com.minhnb.finvera_be.portfolio.dto.PortfolioSummaryResponse;
import com.minhnb.finvera_be.portfolio.dto.RenamePortfolioRequest;
import com.minhnb.finvera_be.portfolio.entity.PortfolioEntity;
import com.minhnb.finvera_be.portfolio.repository.PortfolioRepository;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.DuplicatePortfolioNameException;
import com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.PortfolioNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final OwnerScopedAccess ownerScopedAccess;
    private final PositionService positionService;
    private final Clock clock;

    public PortfolioService(
            PortfolioRepository portfolioRepository,
            OwnerScopedAccess ownerScopedAccess,
            PositionService positionService,
            Clock clock) {
        this.portfolioRepository = portfolioRepository;
        this.ownerScopedAccess = ownerScopedAccess;
        this.positionService = positionService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PortfolioSummaryResponse> listPortfolios() {
        UUID ownerId = ownerScopedAccess.getAuthenticatedOwnerId();
        List<PortfolioEntity> entities = portfolioRepository.findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtAsc(ownerId);
        return positionService.calculatePortfolioSummaries(entities);
    }

    @Transactional
    public PortfolioSummaryResponse createPortfolio(CreatePortfolioRequest request) {
        Objects.requireNonNull(request, "request");
        UUID ownerId = ownerScopedAccess.getAuthenticatedOwnerId();

        String trimmedName = request.name().trim();
        if (portfolioRepository.existsByOwnerIdAndNameAndDeletedAtIsNull(ownerId, trimmedName)) {
            throw new DuplicatePortfolioNameException(trimmedName);
        }

        UUID portfolioId = UUID.randomUUID();
        Instant now = Instant.now(clock);
        PortfolioEntity entity = new PortfolioEntity(portfolioId, ownerId, trimmedName, now, null);
        portfolioRepository.save(entity);

        return new PortfolioSummaryResponse(
                portfolioId,
                entity.getName(),
                entity.getCreatedAt(),
                "0",
                "0",
                "0",
                "0",
                now);
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getPortfolio(UUID portfolioId) {
        Objects.requireNonNull(portfolioId, "portfolioId");
        UUID ownerId = ownerScopedAccess.getAuthenticatedOwnerId();

        PortfolioEntity entity = portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNull(portfolioId, ownerId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));

        return positionService.calculatePortfolioTotals(entity.getId(), entity.getName(), entity.getCreatedAt());
    }

    @Transactional
    public PortfolioSummaryResponse renamePortfolio(UUID portfolioId, RenamePortfolioRequest request) {
        Objects.requireNonNull(portfolioId, "portfolioId");
        Objects.requireNonNull(request, "request");
        UUID ownerId = ownerScopedAccess.getAuthenticatedOwnerId();

        PortfolioEntity entity = portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNull(portfolioId, ownerId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));

        String newName = request.name().trim();
        if (!entity.getName().equals(newName) && portfolioRepository.existsByOwnerIdAndNameAndDeletedAtIsNull(ownerId, newName)) {
            throw new DuplicatePortfolioNameException(newName);
        }

        entity.setName(newName);
        portfolioRepository.save(entity);

        return positionService.calculatePortfolioTotals(entity.getId(), entity.getName(), entity.getCreatedAt());
    }

    @Transactional
    public void deletePortfolio(UUID portfolioId) {
        Objects.requireNonNull(portfolioId, "portfolioId");
        UUID ownerId = ownerScopedAccess.getAuthenticatedOwnerId();

        PortfolioEntity entity = portfolioRepository.findByIdAndOwnerIdAndDeletedAtIsNull(portfolioId, ownerId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));

        entity.markDeleted(Instant.now(clock));
        portfolioRepository.save(entity);
    }
}
