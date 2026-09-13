package com.minhnb.finvera_be.positioning;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.positioning.dto.PositionSizingDtos.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PositionSizingSecurityTests {
    @Test void requestAndResponseStringFormsRedactPrivateFinancialValuesAndIdentifiers() {
        var request = new SizingRequest(Mode.PORTFOLIO, "FPT", UUID.fromString("00000000-0000-0000-0000-000000000001"),
                null, new RiskBudget(RiskKind.FIXED_VND, "123456789"),
                new PriceInput(PriceSource.MANUAL, "54321", "50000", null, null, null, null, null),
                new CostPolicy(true, null, null, null, null, null), null);
        assertThat(request.toString()).doesNotContain("FPT", "123456789", "54321", "00000000-");
        var response = new SizingResult("WITHHELD", "FPT", "PORTFOLIO", null, null, 100, null,
                "123456789", "123456789", "54321", "50000", null, null, true,
                null, null, null, null, null, null, null, null, null, null,
                java.util.List.of(), java.util.List.of(), java.util.List.of("PORTFOLIO_DATA_UNAVAILABLE"),
                java.util.List.of(), "position-sizing-v1", "market-lot-v1", java.time.Instant.EPOCH);
        assertThat(response.toString()).doesNotContain("FPT", "123456789", "54321");
    }
}
