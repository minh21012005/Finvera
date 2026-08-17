package com.minhnb.finvera_be.market.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.AdjustmentStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Venue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MarketValueObjectTests {

    @Test
    void exactDecimalPreservesDeclaredScaleWithoutBinaryFloatingPoint() {
        var value = DecimalValue.exact(new BigDecimal("11250000000000.0000"), "VND", 4);
        assertThat(value.value().toPlainString()).isEqualTo("11250000000000.0000");
        assertThat(value.value()).isInstanceOf(BigDecimal.class);
    }

    @Test
    void exactDecimalRejectsImplicitRoundingAndRoundedValueDeclaresPolicy() {
        assertThatThrownBy(() -> DecimalValue.exact(new BigDecimal("1.2345678"), "PERCENT_POINT", 6))
                .isInstanceOf(ArithmeticException.class);

        var rounded = DecimalValue.rounded(
                new BigDecimal("1.2345678"), "PERCENT_POINT", 6, RoundingMode.HALF_UP);
        assertThat(rounded.value().toPlainString()).isEqualTo("1.234568");
        assertThat(rounded.roundingMode()).isEqualTo(RoundingMode.HALF_UP);
    }

    @Test
    void metadataRequiresExplicitVietnamTimeAndUtcInstants() {
        var observedAt = Instant.parse("2026-08-17T03:00:00Z");
        var metadata = new ObservationMetadata(
                "FINVERA_FIXTURE",
                observedAt,
                observedAt,
                observedAt.plusSeconds(1),
                ObservationMetadata.VIETNAM_MARKET_TIME,
                Currency.getInstance("VND"),
                Venue.HOSE,
                AdjustmentStatus.RAW);

        assertThat(metadata.marketTimezone()).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(metadata.observedAt().toString()).endsWith("Z");
    }

    @Test
    void enumerationsHaveClosedV1SetsAndSeverityOrdering() {
        assertThat(IndexCode.values()).containsExactly(
                IndexCode.VN_INDEX, IndexCode.VN30, IndexCode.HNX_INDEX, IndexCode.UPCOM_INDEX);
        assertThat(DataStatus.mostActionable(DataStatus.STALE, DataStatus.PARTIAL))
                .isEqualTo(DataStatus.PARTIAL);
        assertThat(DataStatus.mostActionable(DataStatus.UNAVAILABLE, DataStatus.CURRENT))
                .isEqualTo(DataStatus.UNAVAILABLE);
    }
}
