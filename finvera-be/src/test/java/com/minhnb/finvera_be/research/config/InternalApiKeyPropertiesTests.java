package com.minhnb.finvera_be.research.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhnb.finvera_be.analyst.config.AnalystProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Constitution "Configuration": a missing secret fails startup and never falls
 * back to something weak. Both internal-API-key property sets refuse a blank
 * value and the former well-known dev placeholder (Q-18, 2026-08-30).
 */
class InternalApiKeyPropertiesTests {

    private static final String PLACEHOLDER = "dev-internal-key-change-in-prod";

    @Test
    void researchPropertiesRefuseBlankKey() {
        assertThatThrownBy(() -> new ResearchProperties(" ", null, null, null, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FINVERA_RESEARCH_INTERNAL_API_KEY");
        assertThatThrownBy(() -> new ResearchProperties(null, null, null, null, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void researchPropertiesRefuseTheFormerPlaceholder() {
        assertThatThrownBy(() -> new ResearchProperties(PLACEHOLDER, null, null, null, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    void researchPropertiesAcceptARealSecret() {
        assertThatCode(() -> new ResearchProperties("a-real-shared-secret", null, null, null, 0))
                .doesNotThrowAnyException();
    }

    @Test
    void analystPropertiesRefuseBlankAndPlaceholder() {
        assertThatThrownBy(() -> new AnalystProperties(null, "", 0, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FINVERA_ANALYST_INTERNAL_API_KEY");
        assertThatThrownBy(() -> new AnalystProperties(null, PLACEHOLDER, 0, null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new AnalystProperties(null, "a-real-shared-secret", 5, Duration.ofSeconds(5), null))
                .doesNotThrowAnyException();
    }
}
