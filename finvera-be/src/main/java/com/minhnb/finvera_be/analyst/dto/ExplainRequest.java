package com.minhnb.finvera_be.analyst.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record ExplainRequest(
        @NotBlank(message = "Output type must not be blank")
        String outputType,
        @NotNull(message = "Evidence factors must not be null")
        Map<String, Object> evidenceFactors,
        String symbol) {
}
