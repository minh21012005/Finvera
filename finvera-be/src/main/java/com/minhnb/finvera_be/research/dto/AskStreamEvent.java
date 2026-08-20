package com.minhnb.finvera_be.research.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AskStreamEvent(
        String type,
        String textDelta,
        AskFinalResult finalResult) {

    public static AskStreamEvent delta(String textDelta) {
        return new AskStreamEvent("delta", textDelta, null);
    }

    public static AskStreamEvent finalResult(AskFinalResult finalResult) {
        return new AskStreamEvent("final", null, finalResult);
    }
}
