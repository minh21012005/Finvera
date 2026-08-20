package com.minhnb.finvera_be.research.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.research.config.InternalApiKeyFilter;
import com.minhnb.finvera_be.research.config.ResearchProperties;
import com.minhnb.finvera_be.research.domain.Applicability;
import com.minhnb.finvera_be.research.domain.ImpactLevel;
import com.minhnb.finvera_be.research.domain.IngestionStatus;
import com.minhnb.finvera_be.research.domain.NewsCategory;
import com.minhnb.finvera_be.research.domain.Sentiment;
import com.minhnb.finvera_be.research.dto.NewsArticlePageResponse;
import com.minhnb.finvera_be.research.dto.NewsArticleResponse;
import com.minhnb.finvera_be.research.dto.SubmitNewsArticleRequest;
import com.minhnb.finvera_be.research.service.NewsArticleService;
import com.minhnb.finvera_be.shared.api.CorrelationIdFilter;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = NewsArticleController.class)
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class, InternalApiKeyFilter.class, CorrelationIdFilter.class, ProblemDetailsAdvice.class})
@EnableConfigurationProperties(ResearchProperties.class)
class NewsArticleControllerTests {

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("finvera.security.owner.id", () -> "00000000-0000-0000-0000-000000000001");
        registry.add("finvera.security.owner.username", () -> "owner-test");
        registry.add("finvera.security.owner.password-hash", () -> "$2a$04$test");
        registry.add("finvera.research.internal-api-key", () -> "dev-internal-key-change-in-prod");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NewsArticleService newsService;

    @Test
    void unauthenticated_request_is_rejected_401() throws Exception {
        mockMvc.perform(get("/api/v1/research/news"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void csrf_token_required_for_submit_and_delete() throws Exception {
        SubmitNewsArticleRequest request = new SubmitNewsArticleRequest(
                "Tiêu đề", "FPT", "VnExpress", Instant.now(), null, "Nội dung");

        mockMvc.perform(post("/api/v1/research/news")
                        .with(user("owner-test").roles("OWNER"))
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/research/news/" + UUID.randomUUID())
                        .with(user("owner-test").roles("OWNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void submit_news_article_success_returns_201() throws Exception {
        UUID id = UUID.randomUUID();
        NewsArticleResponse response = new NewsArticleResponse(
                id,
                "FPT tăng trưởng",
                "FPT",
                "VnExpress",
                null,
                Instant.now(),
                NewsCategory.COMPANY,
                Applicability.DEFINED,
                Sentiment.POSITIVE,
                Applicability.DEFINED,
                ImpactLevel.HIGH,
                Applicability.DEFINED,
                "Technology",
                IngestionStatus.PENDING,
                null,
                Instant.now(),
                null);

        when(newsService.submitNewsArticle(any())).thenReturn(response);

        SubmitNewsArticleRequest request = new SubmitNewsArticleRequest(
                "FPT tăng trưởng", "FPT", "VnExpress", Instant.now(), null, "Nội dung");

        mockMvc.perform(post("/api/v1/research/news")
                        .with(user("owner-test").roles("OWNER"))
                        .with(csrf())
                        .header("Idempotency-Key", "idem-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("FPT tăng trưởng"))
                .andExpect(jsonPath("$.category").value("COMPANY"));
    }

    @Test
    void get_news_article_returns_200() throws Exception {
        UUID id = UUID.randomUUID();
        NewsArticleResponse response = new NewsArticleResponse(
                id,
                "FPT tăng trưởng",
                "FPT",
                "VnExpress",
                null,
                Instant.now(),
                NewsCategory.COMPANY,
                Applicability.DEFINED,
                Sentiment.POSITIVE,
                Applicability.DEFINED,
                ImpactLevel.HIGH,
                Applicability.DEFINED,
                "Technology",
                IngestionStatus.READY,
                null,
                Instant.now(),
                Instant.now());

        when(newsService.getNewsArticle(id)).thenReturn(response);

        mockMvc.perform(get("/api/v1/research/news/" + id)
                        .with(user("owner-test").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.ingestionStatus").value("READY"));
    }

    @Test
    void delete_news_article_returns_204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(newsService).deleteNewsArticle(id);

        mockMvc.perform(delete("/api/v1/research/news/" + id)
                        .with(user("owner-test").roles("OWNER"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }
}
