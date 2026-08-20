package com.minhnb.finvera_be.research.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AskStreamEvent(
        String type,
        String textDelta,
        // Wire key MUST be "final" per public-api.openapi.yaml; the Java field is named
        // finalResult only because "final" is a reserved word and cannot name a record
        // component.
        @JsonProperty("final") AskFinalResult finalResult) {

    public static AskStreamEvent delta(String textDelta) {
        return new AskStreamEvent("delta", textDelta, null);
    }

    public static AskStreamEvent finalResult(AskFinalResult finalResult) {
        return new AskStreamEvent("final", null, finalResult);
    }
}
