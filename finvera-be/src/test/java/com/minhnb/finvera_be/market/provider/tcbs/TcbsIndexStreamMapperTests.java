package com.minhnb.finvera_be.market.provider.tcbs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import org.junit.jupiter.api.Test;

class TcbsIndexStreamMapperTests {
    private final TcbsIndexStreamMapper mapper = new TcbsIndexStreamMapper();

    @Test
    void mapsDocumentedIndexFieldsWithExactDecimalReferenceAndExplicitStreamLimitations() {
        var observation = mapper.map(new TcbsIndexStreamMapper.IndexStreamMessage(
                1, "1300.250000", "2.500000", "0.192700", "1000000", "25000000000", "5"));

        assertThat(observation.code()).isEqualTo(IndexCode.VN_INDEX);
        assertThat(observation.referenceLevel()).isEqualByComparingTo("1297.750000");
        assertThat(observation.matchedVolume()).isEqualTo(1_000_000L);
        assertThat(observation.reasonCodes()).containsExactly(
                "TCBS_STREAM_ORDERING_UNAVAILABLE", "TCBS_STREAM_TIMESTAMP_UNAVAILABLE");
    }

    @Test
    void rejectsUnsupportedIndicesAndInvalidNumericValues() {
        assertThatThrownBy(() -> mapper.map(new TcbsIndexStreamMapper.IndexStreamMessage(
                4, "1.0", "0.0", "0.0", "0", "0", "5")))
                .hasMessage("TCBS_INDEX_NOT_ALLOWLISTED");
        assertThatThrownBy(() -> mapper.map(new TcbsIndexStreamMapper.IndexStreamMessage(
                1, "not-a-number", "0.0", "0.0", "0", "0", "5")))
                .hasMessage("TCBS_DECIMAL_INVALID");
    }
}
