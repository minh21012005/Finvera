package com.minhnb.finvera_be.research.dto;

import com.minhnb.finvera_be.research.domain.DocumentType;
import com.minhnb.finvera_be.research.domain.NewsCategory;
import java.time.LocalDate;

public record RetrieveFilter(
        String symbol,
        DocumentType documentType,
        NewsCategory newsCategory,
        LocalDate dateFrom,
        LocalDate dateTo) {
}
