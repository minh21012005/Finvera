package com.minhnb.finvera_be.portfolio.service;

import com.minhnb.finvera_be.portfolio.dto.PositionResponse;
import com.minhnb.finvera_be.portfolio.dto.PositionsResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owner-scoped application boundary exposing one coherent sizing snapshot. */
@Service
public class PortfolioSizingDataService {
    private final PositionService positions;

    public PortfolioSizingDataService(PositionService positions) {
        this.positions = positions;
    }

    @Transactional(readOnly = true)
    public Snapshot resolve(UUID portfolioId, String symbol) {
        PositionsResponse response = positions.getPositions(portfolioId);
        BigDecimal cash = decimal(response.cashBalance());
        BigDecimal total = decimal(response.totalValue());
        BigDecimal deployed = total == null || cash == null ? null : total.subtract(cash);
        PositionResponse target = response.positions().stream()
                .filter(p -> p.instrumentSymbol().equalsIgnoreCase(symbol)).findFirst().orElse(null);
        BigDecimal targetQuantity = target == null ? BigDecimal.ZERO : decimal(target.quantity());
        BigDecimal targetValue = target == null ? BigDecimal.ZERO
                : targetQuantity == null || target.currentPrice() == null ? null
                : targetQuantity.multiply(decimal(target.currentPrice()));
        return new Snapshot(portfolioId, cash, total, deployed, targetQuantity, targetValue,
                response.dataStatus(), response.reasonCodes(), response.coherenceKey(), response.asOf());
    }

    private static BigDecimal decimal(String value) { return value == null ? null : new BigDecimal(value); }

    public record Snapshot(UUID portfolioId, BigDecimal availableCashVnd, BigDecimal totalValueVnd,
            BigDecimal deployedMarketValueVnd, BigDecimal symbolQuantity, BigDecimal symbolMarketValueVnd,
            String dataStatus, List<String> reasonCodes, String coherenceKey, Instant asOf) { }
}
