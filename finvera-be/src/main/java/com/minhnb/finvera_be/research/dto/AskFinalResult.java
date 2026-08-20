package com.minhnb.finvera_be.research.dto;

import java.util.List;

public record AskFinalResult(
        String answer,
        List<AskCitation> citations,
        boolean refused) {
}
