package com.minhnb.finvera_be.market.provider.tcbs;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.service.TcbsLiveEquityQuoteService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Replays frames captured verbatim from the TCBS Thesis stream during the open session of
 * 2026-09-04 (specs/013-tcbs-live-field-audit, research R-004…R-009; evidence
 * {@code tools/verification/out/tcbs_live_capture_2026-09-04T1106.json}).
 *
 * <p>The point is not to re-test the mapper's rules — {@link TcbsThesisFrameMapperTests} does that
 * with hand-written frames. The point is that these are real provider bytes whose units were
 * anchored against an independent provider (VCI) on the day they were captured, so if TCBS ever
 * switches equity prices to the thousand-VND board unit, changes a field's JSON type, or renumbers
 * an index, this test fails instead of the change reaching PostgreSQL.
 */
class TcbsThesisFrameFixtureTests {

    private static final String FIXTURE = "fixtures/market/tcbs/tcbs-frame-fixture.json";
    private final TcbsThesisFrameMapper mapper = new TcbsThesisFrameMapper();
    private final Instant receivedAt = Instant.parse("2026-09-04T04:07:06Z");

    @Test
    void everyCapturedFrameStillMapsThroughTheProductionMapper() throws IOException {
        Map<String, TcbsThesisFrameMapper.Event> events = replay();

        assertThat(events).containsOnlyKeys("control-open", "control-heartbeat", "trade-liquid",
                "trade-no-change", "index-vn", "index-vn30", "index-hnx", "index-upcom");
        assertThat(events.get("control-open")).isInstanceOf(TcbsThesisFrameMapper.ControlFrame.class);
        assertThat(events.get("control-heartbeat")).isInstanceOf(TcbsThesisFrameMapper.ControlFrame.class);
        // Neither server control frame is an authentication response; the client must not treat
        // d|33 or d|34 as one (research R-008).
        assertThat(((TcbsThesisFrameMapper.ControlFrame) events.get("control-open")).authenticationResponse())
                .isFalse();
        assertThat(((TcbsThesisFrameMapper.ControlFrame) events.get("control-heartbeat")).authenticationResponse())
                .isFalse();
    }

    @Test
    void equityPricesStayInBaseVndPerShare() throws IOException {
        var trade = (TcbsThesisFrameMapper.EquityTradeUpdate) replay().get("trade-liquid");

        // VNM matched at 61,900 VND while VCI reported 61.9 (thousand VND) in the same minute.
        // A board-unit regression would land at 61.9 here; the guard is the magnitude, not the price.
        assertThat(trade.symbol()).isEqualTo("VNM");
        assertThat(trade.matchPrice()).isEqualByComparingTo("61900");
        assertThat(trade.matchPrice()).isGreaterThan(new BigDecimal("1000"));
        assertThat(trade.absoluteChange()).isEqualByComparingTo("700");
        // changePercent is already a percentage, not a fraction (research R-007).
        assertThat(trade.percentageChange()).isBetween(new BigDecimal("1.14"), new BigDecimal("1.15"));
        assertThat(trade.totalVolume()).isEqualTo(801_700L);
        assertThat(trade.totalValueVnd()).isEqualByComparingTo("49391340000");

        // totalValue / totalVolume is a session VWAP in the same unit as matchPrice: the arithmetic
        // identity that proves value is base VND and volume is shares, not lots.
        BigDecimal averagePrice = trade.totalValueVnd()
                .divide(BigDecimal.valueOf(trade.totalVolume()), 0, java.math.RoundingMode.HALF_UP);
        assertThat(averagePrice).isBetween(new BigDecimal("40000"), new BigDecimal("90000"));
    }

    @Test
    void mixedStringAndNumberTypingIsAcceptedWithinOneFrame() throws IOException {
        Map<String, TcbsThesisFrameMapper.Event> events = replay();
        var trade = (TcbsThesisFrameMapper.EquityTradeUpdate) events.get("trade-liquid");
        var index = (TcbsThesisFrameMapper.IndexUpdate) events.get("index-hnx");

        // The provider sends prices/volumes as JSON strings and changes as JSON numbers in the very
        // same payload, and index volume/value in scientific notation (research R-006).
        assertThat(rawField("trade-liquid", "matchPrice").isString()).isTrue();
        assertThat(rawField("trade-liquid", "change").isNumber()).isTrue();
        assertThat(trade.matchPrice()).isNotNull();
        assertThat(trade.absoluteChange()).isNotNull();
        assertThat(index.matchedVolume()).isEqualTo(16_469_416L);
        assertThat(index.matchedValueVnd()).isEqualByComparingTo("239539258000");
    }

    @Test
    void anEquityFrameWithoutChangeLeavesTheReferenceUnknownRatherThanZero() throws IOException {
        var trade = (TcbsThesisFrameMapper.EquityTradeUpdate) replay().get("trade-no-change");

        // Real captured frame: MBB traded with no change/changePercent at all. Missing must stay
        // missing — this is the frame for which the derived-reference fallback cannot fire, so the
        // quote waits for tickerCommons instead of publishing a fabricated reference (R-005).
        assertThat(trade.symbol()).isEqualTo("MBB");
        assertThat(trade.matchPrice()).isEqualByComparingTo("20600");
        assertThat(trade.absoluteChange()).isNull();
        assertThat(trade.percentageChange()).isNull();
    }

    @Test
    void indexNumbersMapToTheCodesTheSessionProved() throws IOException {
        Map<String, TcbsThesisFrameMapper.Event> events = replay();

        // Anchored against VCI in the same minute: 1846.77 VNINDEX, 1976.15 VN30, 282.12 HNXINDEX,
        // 127.61 UPCOMINDEX. Levels are index points on both sides (research R-004).
        assertIndex(events.get("index-vn"), IndexCode.VN_INDEX, "1846.77");
        assertIndex(events.get("index-vn30"), IndexCode.VN30, "1976.15");
        assertIndex(events.get("index-hnx"), IndexCode.HNX_INDEX, "282.12");
        assertIndex(events.get("index-upcom"), IndexCode.UPCOM_INDEX, "127.61");

        var vn30 = (TcbsThesisFrameMapper.IndexUpdate) events.get("index-vn30");
        var vnIndex = (TcbsThesisFrameMapper.IndexUpdate) events.get("index-vn");
        // Breadth is present on every index, but only VN/HNX/UPCoM carry the ceil/floor counters;
        // VN30 never does. Finvera consolidates 1+3+5 only, so VN30's breadth is read, not summed.
        assertThat(vn30.breadth()).isNotNull();
        assertThat(vnIndex.breadth()).isNotNull();
        assertThat(rawField("index-vn", "ceilIncrease").isMissingNode()).isFalse();
        assertThat(rawField("index-vn30", "ceilIncrease").isMissingNode()).isTrue();
    }

    @Test
    void referenceLevelIsDerivedFromTheCapturedLevelAndChange() throws IOException {
        var index = (TcbsThesisFrameMapper.IndexUpdate) replay().get("index-vn");

        assertThat(index.referenceLevel())
                .isEqualByComparingTo(index.level().subtract(index.absoluteChange()));
        assertThat(index.referenceLevel()).isGreaterThan(BigDecimal.ZERO);
        // The opaque provider session code is carried, never interpreted.
        assertThat(index.rawProviderSession()).isNotBlank();
    }

    @Test
    void tickerCommonsSnapshotIsBaseVndAndSitsOnTheVenuePriceLimitBand() throws IOException {
        var response = JsonMapper.builder().build().treeToValue(
                fixture().get("tickerCommons").get("response"),
                TcbsLiveEquityQuoteService.TickerCommonsResponse.class);

        assertThat(response.data()).hasSize(3);
        // The REST snapshot is stored with no unit conversion and is the only source of
        // ceiling/floor/foreign room for a mid-session symbol (research R-005/R-010), so its unit
        // has to be pinned as hard as the stream's.
        Map<String, BigDecimal> expectedBand = Map.of("VNM", new BigDecimal("0.07"),
                "MBB", new BigDecimal("0.07"), "ACV", new BigDecimal("0.15"));
        for (var item : response.data()) {
            BigDecimal reference = item.refPrice();
            assertThat(reference).isGreaterThan(new BigDecimal("1000"));
            assertThat(item.matchPrice()).isGreaterThan(new BigDecimal("1000"));

            // ceilPrice/floorPrice are the venue's price-limit band around refPrice, rounded to the
            // tick. That identity holds only if all three are in the same unit — an anchor that owes
            // nothing to any provider label.
            BigDecimal band = expectedBand.get(item.symbol());
            BigDecimal ceilingBand = item.ceilPrice().divide(reference, 4, java.math.RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE);
            BigDecimal floorBand = BigDecimal.ONE
                    .subtract(item.floorPrice().divide(reference, 4, java.math.RoundingMode.HALF_UP));
            assertThat(ceilingBand).isCloseTo(band, org.assertj.core.data.Offset.offset(new BigDecimal("0.005")));
            assertThat(floorBand).isCloseTo(band, org.assertj.core.data.Offset.offset(new BigDecimal("0.005")));

            // Intraday extremes bracket the last match, and foreign room is a share count.
            assertThat(item.high()).isGreaterThanOrEqualTo(item.matchPrice());
            assertThat(item.low()).isLessThanOrEqualTo(item.matchPrice());
            assertThat(item.room()).isNotNull().isPositive();
            assertThat(item.totalVol()).isNotNull().isPositive();
        }
    }

    private void assertIndex(TcbsThesisFrameMapper.Event event, IndexCode code, String level) {
        assertThat(event).isInstanceOf(TcbsThesisFrameMapper.IndexUpdate.class);
        var index = (TcbsThesisFrameMapper.IndexUpdate) event;
        assertThat(index.code()).isEqualTo(code);
        assertThat(index.level()).isEqualByComparingTo(level);
    }

    private Map<String, TcbsThesisFrameMapper.Event> replay() throws IOException {
        Map<String, TcbsThesisFrameMapper.Event> events = new LinkedHashMap<>();
        for (JsonNode frame : fixture().get("frames")) {
            events.put(frame.get("id").stringValue(), mapper.map(frame.get("raw").stringValue(), receivedAt));
        }
        return events;
    }

    private JsonNode rawField(String frameId, String field) throws IOException {
        for (JsonNode frame : fixture().get("frames")) {
            if (frame.get("id").stringValue().equals(frameId)) {
                String raw = frame.get("raw").stringValue();
                return JsonMapper.builder().build().readTree(raw.split("\\|", 3)[2]).path(field);
            }
        }
        throw new IllegalArgumentException("no fixture frame " + frameId);
    }

    private JsonNode fixture() throws IOException {
        try (var input = TcbsThesisFrameFixtureTests.class.getClassLoader().getResourceAsStream(FIXTURE)) {
            if (input == null) throw new IllegalStateException(FIXTURE + " is missing");
            return JsonMapper.builder().build().readTree(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
