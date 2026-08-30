package com.minhnb.finvera_be.market.provider.tcbs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TcbsThesisFrameMapperTests {

    private final TcbsThesisFrameMapper mapper = new TcbsThesisFrameMapper();
    private final Instant receivedAt = Instant.parse("2026-08-24T03:00:01Z");

    @Test
    void mapsOfficialIndexFrameWithoutScalingOrGuessingProviderSession() {
        var event = mapper.map("s|8|{\"indexNumber\":1,\"index\":1728.25,\"change\":12.5,"
                + "\"changePercent\":0.7286,\"volume\":55936000,\"value\":728709419900,"
                + "\"increase\":246,\"decrease\":58,\"notChange\":63,\"session\":\"5\","
                + "\"ceilIncrease\":19,\"floorDecrease\":8}", receivedAt);

        assertThat(event).isInstanceOf(TcbsThesisFrameMapper.IndexUpdate.class);
        var index = (TcbsThesisFrameMapper.IndexUpdate) event;
        assertThat(index.code()).isEqualTo(IndexCode.VN_INDEX);
        assertThat(index.level()).isEqualByComparingTo("1728.25");
        assertThat(index.referenceLevel()).isEqualByComparingTo("1715.75");
        assertThat(index.matchedVolume()).isEqualTo(55_936_000L);
        assertThat(index.matchedValueVnd()).isEqualByComparingTo("728709419900");
        assertThat(index.breadth()).isEqualTo(new TcbsThesisFrameMapper.BreadthCounts(246, 58, 63));
        assertThat(index.rawProviderSession()).isEqualTo("5");
        assertThat(index.receivedAt()).isEqualTo(receivedAt);
    }

    @Test
    void mapsAllFourDocumentedIndexNumbers() {
        assertThat(indexCode(1)).isEqualTo(IndexCode.VN_INDEX);
        assertThat(indexCode(2)).isEqualTo(IndexCode.VN30);
        assertThat(indexCode(3)).isEqualTo(IndexCode.HNX_INDEX);
        assertThat(indexCode(5)).isEqualTo(IndexCode.UPCOM_INDEX);
    }

    @Test
    void acceptsProviderDecimalsAsStringsOrNumbers() {
        var event = (TcbsThesisFrameMapper.EquityTradeUpdate) mapper.map(
                "s|6|{\"symbol\":\"TCB\",\"matchPrice\":\"24550\",\"matchQtty\":\"5000\","
                        + "\"change\":200.0,\"changePercent\":\"0.821355\","
                        + "\"totalVolume\":\"9745500\",\"totalValue\":240774655000}", receivedAt);

        assertThat(event.symbol()).isEqualTo("TCB");
        assertThat(event.matchPrice()).isEqualByComparingTo(new BigDecimal("24550"));
        assertThat(event.totalVolume()).isEqualTo(9_745_500L);
        assertThat(event.totalValueVnd()).isEqualByComparingTo("240774655000");
    }

    @Test
    void mapsReferencePriceAndIgnoresControlFrames() {
        var reference = (TcbsThesisFrameMapper.EquityReferenceUpdate) mapper.map(
                "s|4|{\"symbol\":\"VPD\",\"ceilPrice\":25000,\"floorPrice\":24100,\"refPrice\":24300}",
                receivedAt);
        assertThat(reference.referencePrice()).isEqualByComparingTo("24300");
        assertThat(reference.ceilingPrice()).isEqualByComparingTo("25000");
        assertThat(reference.floorPrice()).isEqualByComparingTo("24100");
        assertThat(mapper.map("d|33|15", receivedAt)).isInstanceOf(TcbsThesisFrameMapper.ControlFrame.class);
    }

    @Test
    void rejectsUnknownIndexAndInvalidFinancialValues() {
        assertThatThrownBy(() -> mapper.map(
                "s|8|{\"indexNumber\":99,\"index\":100,\"change\":1}", receivedAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("indexNumber");
        assertThatThrownBy(() -> mapper.map(
                "s|8|{\"indexNumber\":1,\"index\":-1,\"change\":1}", receivedAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("index");
    }

    private IndexCode indexCode(int indexNumber) {
        var event = (TcbsThesisFrameMapper.IndexUpdate) mapper.map(
                "s|8|{\"indexNumber\":" + indexNumber + ",\"index\":100,\"change\":1}", receivedAt);
        return event.code();
    }
}
