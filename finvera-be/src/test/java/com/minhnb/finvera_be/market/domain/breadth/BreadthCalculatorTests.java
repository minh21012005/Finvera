package com.minhnb.finvera_be.market.domain.breadth;

import static com.minhnb.finvera_be.market.domain.breadth.BreadthUniversePolicy.InstrumentType.COMMON_EQUITY;
import static com.minhnb.finvera_be.market.domain.breadth.BreadthUniversePolicy.InstrumentType.ETF;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.AdjustmentStatus.PROVIDER_ADJUSTED;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.AdjustmentStatus.RAW;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.Venue.HNX;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.Venue.HOSE;
import static com.minhnb.finvera_be.market.domain.model.MarketTypes.Venue.UPCOM;
import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.market.domain.breadth.BreadthCalculator.SecurityInput;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BreadthCalculatorTests {

    private final BreadthCalculator calculator = new BreadthCalculator(new BreadthUniversePolicy());

    @Test
    void classifiesEligibleCommonEquitiesUsingUnroundedOfficialReferencePrices() {
        var result = calculator.calculate(List.of(
                security(HOSE, "ADV", "VN000ADV001", false, COMMON_EQUITY, "10.000001", "10.000000", RAW),
                security(HNX, "DEC", "VN000DEC001", false, COMMON_EQUITY, "9.999999", "10.000000", RAW),
                security(UPCOM, "FLAT", "VN000FLT001", false, COMMON_EQUITY, "10.000000", "10.000000", RAW),
                security(HOSE, "NOREF", "VN000NREF01", false, COMMON_EQUITY, "11.000000", null, RAW),
                security(HOSE, "ETF1", "VN000ETF001", false, ETF, "11.000000", "10.000000", RAW)));

        assertThat(result.advancing()).isEqualTo(1);
        assertThat(result.declining()).isEqualTo(1);
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.unclassified()).isEqualTo(1);
        assertThat(result.eligible()).isEqualTo(4);
        assertThat(result.reasonCodes()).contains("MISSING_REFERENCE_PRICE");
        assertReconciles(result);
    }

    @Test
    void countsAnIsinOnlyOnceEvenWhenItIsAlsoAVn30Member() {
        var result = calculator.calculate(List.of(
                security(HOSE, "VNM", "VN000VNM001", false, COMMON_EQUITY, "60.100000", "60.000000", RAW),
                security(HOSE, "VNM", "VN000VNM001", true, COMMON_EQUITY, "60.100000", "60.000000", RAW)));

        assertThat(result.advancing()).isEqualTo(1);
        assertThat(result.eligible()).isEqualTo(1);
        assertReconciles(result);
    }

    @Test
    void fallsBackToVenueAndSymbolOnlyWhenIsinIsMissing() {
        var result = calculator.calculate(List.of(
                security(HOSE, "ABC", null, false, COMMON_EQUITY, "10.100000", "10.000000", RAW),
                security(HOSE, "ABC", null, true, COMMON_EQUITY, "10.100000", "10.000000", RAW),
                security(HNX, "ABC", null, false, COMMON_EQUITY, "9.900000", "10.000000", RAW)));

        assertThat(result.advancing()).isEqualTo(1);
        assertThat(result.declining()).isEqualTo(1);
        assertThat(result.eligible()).isEqualTo(2);
        assertReconciles(result);
    }

    @Test
    void trustsTheProviderOfficialExRightReferenceInsteadOfRecalculatingIt() {
        var result = calculator.calculate(List.of(
                security(HOSE, "EXR", "VN000EXR001", false, COMMON_EQUITY, "8.500000", "8.500000", PROVIDER_ADJUSTED)));

        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.advancing()).isZero();
        assertThat(result.declining()).isZero();
        assertReconciles(result);
    }

    @Test
    void reconcilesEveryEligibleSecurityAcrossClassificationBoundaries() {
        var inputs = List.of(
                security(HOSE, "A", "VN000000001", false, COMMON_EQUITY, "1.000001", "1.000000", RAW),
                security(HNX, "B", "VN000000002", false, COMMON_EQUITY, "0.999999", "1.000000", RAW),
                security(UPCOM, "C", "VN000000003", false, COMMON_EQUITY, "1.000000", "1.000000", RAW),
                security(HOSE, "D", "VN000000004", false, COMMON_EQUITY, null, "1.000000", RAW));

        var result = calculator.calculate(inputs);

        assertThat(result.reasonCodes()).contains("MISSING_PRICE");
        assertReconciles(result);
    }

    private static SecurityInput security(
            com.minhnb.finvera_be.market.domain.model.MarketTypes.Venue venue,
            String symbol,
            String isin,
            boolean vn30Member,
            BreadthUniversePolicy.InstrumentType instrumentType,
            String matchedOrClosePrice,
            String officialReferencePrice,
            com.minhnb.finvera_be.market.domain.model.MarketTypes.AdjustmentStatus adjustmentStatus) {
        return new SecurityInput(venue, symbol, isin, true, vn30Member, instrumentType,
                decimal(matchedOrClosePrice), decimal(officialReferencePrice), adjustmentStatus);
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static void assertReconciles(BreadthCalculator.Result result) {
        assertThat(result.advancing() + result.declining() + result.unchanged() + result.unclassified())
                .isEqualTo(result.eligible());
    }
}
