package com.minhnb.finvera_be.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.minhnb.finvera_be.portfolio.dto.PositionResponse;
import com.minhnb.finvera_be.portfolio.dto.PositionsResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioSizingDataServiceTests {
    @Test void derivesEverySizingValueFromOneCoherentOwnerScopedResponse() {
        PositionService positions = mock(PositionService.class);
        UUID id = UUID.randomUUID();
        given(positions.getPositions(id)).willReturn(new PositionsResponse(List.of(
                new PositionResponse("FPT", "500", "40000", "50000", "DEFINED", "CURRENT",
                        LocalDate.parse("2026-09-11"), "5000000", "0", "0.25")),
                "30000000", "100000000", "CURRENT", List.of(), "coherence-1", Instant.parse("2026-09-12T08:00:00Z")));
        var snapshot = new PortfolioSizingDataService(positions).resolve(id, "FPT");
        assertThat(snapshot.availableCashVnd()).isEqualByComparingTo("30000000");
        assertThat(snapshot.deployedMarketValueVnd()).isEqualByComparingTo("70000000");
        assertThat(snapshot.symbolMarketValueVnd()).isEqualByComparingTo("25000000");
        assertThat(snapshot.coherenceKey()).isEqualTo("coherence-1");
    }

    @Test void preservesAnIncompleteResponseForTruthfulWithholdingInsteadOfThrowing() {
        PositionService positions = mock(PositionService.class);
        UUID id = UUID.randomUUID();
        given(positions.getPositions(id)).willReturn(new PositionsResponse(List.of(),
                null, null, "UNAVAILABLE", List.of("MISSING_VALUE"), null, null));

        var snapshot = new PortfolioSizingDataService(positions).resolve(id, "FPT");

        assertThat(snapshot.availableCashVnd()).isNull();
        assertThat(snapshot.totalValueVnd()).isNull();
        assertThat(snapshot.deployedMarketValueVnd()).isNull();
        assertThat(snapshot.dataStatus()).isEqualTo("UNAVAILABLE");
    }
}
