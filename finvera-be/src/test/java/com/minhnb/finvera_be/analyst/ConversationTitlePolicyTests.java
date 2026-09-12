package com.minhnb.finvera_be.analyst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhnb.finvera_be.analyst.domain.ConversationTitlePolicy;
import org.junit.jupiter.api.Test;

class ConversationTitlePolicyTests {
    @Test void normalizesWhitespaceAndUsesWordBoundary() {
        String result=ConversationTitlePolicy.automaticTitle("  Phân tích   một doanh nghiệp " + "rất ".repeat(30));
        assertThat(result).doesNotContain("  ").hasSizeLessThanOrEqualTo(80);
        assertThat(result).doesNotEndWith(" ");
    }
    @Test void validatesManualTitleBoundaries() {
        assertThat(ConversationTitlePolicy.ownerTitle("x".repeat(120))).hasSize(120);
        assertThatThrownBy(() -> ConversationTitlePolicy.ownerTitle("x".repeat(121)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConversationTitlePolicy.ownerTitle("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
