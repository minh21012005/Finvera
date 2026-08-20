package com.minhnb.finvera_be.research.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record SubmitNewsArticleRequest(
        @NotBlank(message = "Title must not be blank")
        @Size(max = 300, message = "Title must not exceed 300 characters")
        String title,
        String symbol,
        @NotBlank(message = "Source must not be blank")
        @Size(max = 200, message = "Source must not exceed 200 characters")
        String source,
        @NotNull(message = "Published at must not be null")
        Instant publishedAt,
        String referenceUrl,
        @NotBlank(message = "Body must not be blank")
        String body) {
}
