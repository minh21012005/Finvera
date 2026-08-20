package com.minhnb.finvera_be.research.controller;

import com.minhnb.finvera_be.research.domain.NewsCategory;
import com.minhnb.finvera_be.research.domain.Sentiment;
import com.minhnb.finvera_be.research.dto.NewsArticlePageResponse;
import com.minhnb.finvera_be.research.dto.NewsArticleResponse;
import com.minhnb.finvera_be.research.dto.SubmitNewsArticleCommand;
import com.minhnb.finvera_be.research.dto.SubmitNewsArticleRequest;
import com.minhnb.finvera_be.research.service.NewsArticleService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/research/news")
public class NewsArticleController {

    private final NewsArticleService newsService;

    public NewsArticleController(NewsArticleService newsService) {
        this.newsService = newsService;
    }

    @PostMapping
    public ResponseEntity<NewsArticleResponse> submitNewsArticle(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SubmitNewsArticleRequest request) {

        SubmitNewsArticleCommand command = new SubmitNewsArticleCommand(
                request.title(),
                request.symbol(),
                request.source(),
                request.referenceUrl(),
                request.publishedAt(),
                request.body(),
                idempotencyKey);

        NewsArticleResponse response = newsService.submitNewsArticle(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<NewsArticlePageResponse> listNewsArticles(
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestParam(value = "category", required = false) NewsCategory category,
            @RequestParam(value = "sentiment", required = false) Sentiment sentiment,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        NewsArticlePageResponse response = newsService.listNewsArticles(symbol, category, sentiment, dateFrom, dateTo, limit, offset);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{newsArticleId}")
    public ResponseEntity<NewsArticleResponse> getNewsArticle(
            @PathVariable("newsArticleId") UUID newsArticleId) {
        NewsArticleResponse response = newsService.getNewsArticle(newsArticleId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{newsArticleId}")
    public ResponseEntity<Void> deleteNewsArticle(
            @PathVariable("newsArticleId") UUID newsArticleId) {
        newsService.deleteNewsArticle(newsArticleId);
        return ResponseEntity.noContent().build();
    }
}
