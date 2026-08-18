package com.minhnb.finvera_be.market.provider.tcbs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhnb.finvera_be.market.domain.breadth.BreadthUniversePolicy.InstrumentType;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.Venue;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TcbsOuranosC001MapperTests {
    private final TcbsOuranosC001Mapper mapper = new TcbsOuranosC001Mapper();

    @Test
    void mapsTimestampedC001FactIntoBreadthInputWithExplicitProvenanceLimitations() {
        var mapped = mapper.map(new TcbsOuranosC001Mapper.EquityContext(
                        Venue.HOSE, "TCB", null, true, true, InstrumentType.COMMON_EQUITY),
                new TcbsOuranosC001Mapper.C001Message(
                        "TCB", "38600.0", "37500.0", "24277600", "933400610000", "1755144180", "60"));

        assertThat(mapped.observedAt()).isEqualTo(Instant.ofEpochSecond(1_755_144_180L));
        assertThat(mapped.breadthInput().matchedOrClosePrice()).isEqualByComparingTo("38600.000000");
        assertThat(mapped.breadthInput().officialReferencePrice()).isEqualByComparingTo("37500.000000");
        assertThat(mapped.cumulativeVolume()).isEqualTo(24_277_600L);
        assertThat(mapped.reasonCodes()).containsExactly(
                "TCBS_OURANOS_ORDERING_UNAVAILABLE", "TCBS_OURANOS_CORRECTION_UNAVAILABLE");
    }

    @Test
    void rejectsMismatchedSymbolInvalidTimeframeAndInvalidTimestamp() {
        var context = new TcbsOuranosC001Mapper.EquityContext(
                Venue.HOSE, "TCB", null, true, false, InstrumentType.COMMON_EQUITY);

        assertThatThrownBy(() -> mapper.map(context, new TcbsOuranosC001Mapper.C001Message(
                "VNM", "1", "1", "1", "1", "1755144180", "60")))
                .hasMessage("TCBS_OURANOS_SYMBOL_MISMATCH");
        assertThatThrownBy(() -> mapper.map(context, new TcbsOuranosC001Mapper.C001Message(
                "TCB", "1", "1", "1", "1", "1755144180", "30")))
                .hasMessage("TCBS_OURANOS_TIMEFRAME_UNSUPPORTED");
        assertThatThrownBy(() -> mapper.map(context, new TcbsOuranosC001Mapper.C001Message(
                "TCB", "1", "1", "1", "1", "not-epoch", "60")))
                .hasMessage("TCBS_OURANOS_TIMESTAMP_INVALID");
    }
}
