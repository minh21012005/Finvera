package com.minhnb.finvera_be.research.dto;

import com.minhnb.finvera_be.research.domain.DocumentType;
import java.time.LocalDate;

public record SubmitDocumentCommand(
        String title,
        String symbol,
        DocumentType documentType,
        int year,
        Integer quarter,
        String source,
        LocalDate publicationDate,
        byte[] fileContent,
        String originalFilename,
        String pastedText,
        String idempotencyKey) {
}
