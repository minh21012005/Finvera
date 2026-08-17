package com.minhnb.finvera_be.market.domain.index;

import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.CURRENT;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.DELAYED;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.PARTIAL;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus.UNAVAILABLE;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.Direction.UP;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode.HNX_INDEX;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode.UPCOM_INDEX;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode.VN30;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode.VN_INDEX;
import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.index.IndexOverviewCalculator.IndexInput;
import com.minhnb.finvera_be.market.domain.index.IndexOverviewCalculator.SnapshotInput;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.SessionState;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class IndexOverviewTests {

    private final IndexOverviewCalculator calculator = new IndexOverviewCalculator();

    @Test
    void calculatesFromUnroundedLevelAndOfficialReferenceInStableOrder() {
        IndexOverview overview = calculator.calculate(snapshot(CURRENT, 1, List.of(
                input(UPCOM_INDEX, "98.420000", "98.420000"),
                input(HNX_INDEX, "241.120000", "242.000000"),
                input(VN_INDEX, "1280.250000", "1275.000000"),
                input(VN30, "1342.800000", "1338.400000"))));

        assertThat(overview.indices()).extracting(IndexOverview.IndexFact::code)
                .containsExactly(VN_INDEX, VN30, HNX_INDEX, UPCOM_INDEX);
        var vnIndex = overview.indices().getFirst();
        assertThat(vnIndex.absoluteChange()).isEqualByComparingTo("5.250000");
        assertThat(vnIndex.percentageChange()).isEqualByComparingTo("0.411765");
        assertThat(vnIndex.direction()).isEqualTo(UP);
    }

    @Test
    void missingComparisonBasisNeverInventsChangeOrDirection() {
        var inputs = completeInputs();
        inputs.set(0, new IndexInput(VN_INDEX, decimal("1280.250000"), null, 1L, decimal("1.0000"), List.of()));

        var fact = calculator.calculate(snapshot(CURRENT, 1, inputs)).indices().getFirst();

        assertThat(fact.absoluteChange()).isNull();
        assertThat(fact.percentageChange()).isNull();
        assertThat(fact.direction()).isNull();
        assertThat(fact.dataStatus()).isEqualTo(PARTIAL);
        assertThat(fact.reasonCodes()).containsExactly("MISSING_REFERENCE_LEVEL");
    }

    @Test
    void absentIndexBecomesExplicitUnavailableWithoutHidingUsableIndices() {
        List<IndexInput> threeIndices = completeInputs().stream()
                .filter(input -> input.code() != UPCOM_INDEX)
                .toList();

        IndexOverview overview = calculator.calculate(snapshot(PARTIAL, 1, threeIndices));

        assertThat(overview.indices()).hasSize(4);
        assertThat(overview.indices().subList(0, 3)).allMatch(fact -> fact.level() != null);
        assertThat(overview.indices().getLast().dataStatus()).isEqualTo(UNAVAILABLE);
        assertThat(overview.indices().getLast().reasonCodes()).containsExactly("MISSING_INDEX");
    }

    @Test
    void closedAndDelayedSnapshotsPreserveOneCoherentEnvelope() {
        var closed = new SnapshotInput(
                LocalDate.of(2026, 8, 15), Instant.parse("2026-08-15T08:00:00Z"),
                SessionState.CLOSED, DELAYED, 7, "FINVERA_FIXTURE", completeInputs());

        IndexOverview overview = calculator.calculate(closed);

        assertThat(overview.sessionState()).isEqualTo(SessionState.CLOSED);
        assertThat(overview.dataStatus()).isEqualTo(DELAYED);
        assertThat(overview.revision()).isEqualTo(7);
        assertThat(overview.indices()).allMatch(fact -> fact.dataStatus() == DELAYED);
    }

    @Test
    void correctionCreatesAVisibleNewRevisionWithoutMutatingPriorResult() {
        IndexOverview original = calculator.calculate(snapshot(CURRENT, 1, completeInputs()));
        var correctedInputs = completeInputs();
        correctedInputs.set(0, input(VN_INDEX, "1280.300000", "1275.000000"));
        IndexOverview corrected = calculator.calculate(snapshot(CURRENT, 2, correctedInputs));

        assertThat(original.indices().getFirst().level()).isEqualByComparingTo("1280.250000");
        assertThat(corrected.indices().getFirst().level()).isEqualByComparingTo("1280.300000");
        assertThat(corrected.revision()).isEqualTo(2);
    }

    private static SnapshotInput snapshot(DataStatus status, long revision, List<IndexInput> indices) {
        return new SnapshotInput(
                LocalDate.of(2026, 8, 17), Instant.parse("2026-08-17T03:00:00Z"),
                SessionState.OPEN, status, revision, "FINVERA_FIXTURE", indices);
    }

    private static java.util.ArrayList<IndexInput> completeInputs() {
        return new java.util.ArrayList<>(List.of(
                input(VN_INDEX, "1280.250000", "1275.000000"),
                input(VN30, "1342.800000", "1338.400000"),
                input(HNX_INDEX, "241.120000", "242.000000"),
                input(UPCOM_INDEX, "98.420000", "98.420000")));
    }

    private static IndexInput input(com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode code,
                                    String level, String reference) {
        return new IndexInput(code, decimal(level), decimal(reference), 1L, decimal("1.0000"), List.of());
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
