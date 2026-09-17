package com.minhnb.finvera_be.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class OffsetPageRequestTests {
    @Test void preservesAbsoluteNonMultipleOffset() {
        var page=OffsetPageRequest.of(7,20);
        assertThat(page.getOffset()).isEqualTo(7);
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.next().getOffset()).isEqualTo(27);
    }
}
