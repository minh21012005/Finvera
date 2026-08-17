package com.minhnb.finvera_be.market.infrastructure.provider;

import static com.minhnb.finvera_be.market.application.port.out.MarketDataProvider.ProviderHealthState.DEGRADED;
import static com.minhnb.finvera_be.market.application.port.out.MarketDataProvider.ProviderHealthState.READY;
import static com.minhnb.finvera_be.market.infrastructure.provider.fixture.FixtureMarketDataProvider.FixtureScenario.AUTH_REQUIRED;
import static com.minhnb.finvera_be.market.infrastructure.provider.fixture.FixtureMarketDataProvider.FixtureScenario.COMPLETE;
import static com.minhnb.finvera_be.market.infrastructure.provider.fixture.FixtureMarketDataProvider.FixtureScenario.MISSING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhnb.finvera_be.market.application.port.out.MarketDataProvider;
import com.minhnb.finvera_be.market.application.port.out.MarketDataProvider.ProviderAuthenticationRequiredException;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.IndexCode;
import com.minhnb.finvera_be.market.infrastructure.provider.fixture.FixtureMarketDataProvider;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FixtureMarketDataProviderTests {

    private static final LocalDate TRADING_DATE = LocalDate.of(2026, 8, 17);

    @Test
    void readOnlyPortExposesOnlyAllowlistedMarketOperations() {
        Set<String> operations = Arrays.stream(MarketDataProvider.class.getDeclaredMethods())
                .filter(method -> Modifier.isAbstract(method.getModifiers()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertThat(operations).containsExactlyInAnyOrder(
                "fetchReferenceData", "reconcileLatest", "subscribe", "health");
        assertThat(operations).noneMatch(name -> name.matches(
                ".*(order|trade|cash|account|portfolio|withdraw).*"));
    }

    @Test
    void completeFixtureMapsExactDecimalsAndFourAllowlistedIndices() {
        var provider = new FixtureMarketDataProvider(COMPLETE);
        var batch = provider.reconcileLatest(TRADING_DATE);

        assertThat(batch.source()).isEqualTo(FixtureMarketDataProvider.SOURCE);
        assertThat(batch.dataStatus()).isEqualTo(DataStatus.CURRENT);
        assertThat(batch.observations()).extracting(MarketDataProvider.ProviderObservation::code)
                .containsExactly(IndexCode.VN_INDEX, IndexCode.VN30, IndexCode.HNX_INDEX, IndexCode.UPCOM_INDEX);
        assertThat(batch.observations().getFirst().level())
                .isEqualByComparingTo(new BigDecimal("1280.250000"));
        assertThat(batch.observations().getFirst().matchedValueVnd())
                .isEqualByComparingTo(new BigDecimal("11250000000000.0000"));
        assertThat(provider.health().state()).isEqualTo(READY);
    }

    @Test
    void missingDataRemainsNullAndMakesProviderDegraded() {
        var provider = new FixtureMarketDataProvider(MISSING);
        var upcom = provider.reconcileLatest(TRADING_DATE).observations().getLast();

        assertThat(upcom.level()).isNull();
        assertThat(upcom.reasonCodes()).containsExactly("SOURCE_VALUE_MISSING");
        assertThat(provider.health().state()).isEqualTo(DEGRADED);
    }

    @Test
    void authRequiredNeverFallsBackToFixtureFacts() {
        var provider = new FixtureMarketDataProvider(AUTH_REQUIRED);

        assertThat(provider.health().state())
                .isEqualTo(MarketDataProvider.ProviderHealthState.AUTH_REQUIRED);
        assertThatThrownBy(() -> provider.reconcileLatest(TRADING_DATE))
                .isInstanceOf(ProviderAuthenticationRequiredException.class)
                .hasMessage("Provider authentication is required");
    }

    @Test
    void arbitraryFixturePathsCannotEnterTheAdapter() {
        assertThat(FixtureMarketDataProvider.class.getConstructors())
                .allMatch(constructor -> Arrays.equals(
                        constructor.getParameterTypes(),
                        new Class<?>[] {FixtureMarketDataProvider.FixtureScenario.class}));
    }
}
