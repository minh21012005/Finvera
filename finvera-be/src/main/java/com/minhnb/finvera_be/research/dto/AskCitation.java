package com.minhnb.finvera_be.research.dto;

import java.util.UUID;

public record AskCitation(
        String claimText,
        SourceType sourceType,
        UUID sourceId,
        String sourceTitle,
        String location,
        String source) {
}
