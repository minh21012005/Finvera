package com.minhnb.finvera_be.research.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhnb.finvera_be.auth.config.OwnerSecurityConfiguration;
import com.minhnb.finvera_be.auth.service.OwnerSessionService;
import com.minhnb.finvera_be.research.config.InternalApiKeyFilter;
import com.minhnb.finvera_be.research.config.ResearchProperties;
import com.minhnb.finvera_be.research.domain.DocumentType;
import com.minhnb.finvera_be.research.domain.IngestionStatus;
import com.minhnb.finvera_be.research.dto.DocumentPageResponse;
import com.minhnb.finvera_be.research.dto.DocumentResponse;
import com.minhnb.finvera_be.research.dto.PassageResponse;
import com.minhnb.finvera_be.research.dto.RetrieveResponse;
import com.minhnb.finvera_be.research.dto.SourceType;
import com.minhnb.finvera_be.research.service.ResearchDocumentService;
import com.minhnb.finvera_be.research.service.RetrievalService;
import com.minhnb.finvera_be.shared.api.CorrelationIdFilter;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {ResearchDocumentController.class, ResearchRetrievalController.class})
@Import({OwnerSecurityConfiguration.class, OwnerSessionService.class, InternalApiKeyFilter.class, CorrelationIdFilter.class, ProblemDetailsAdvice.class})
@EnableConfigurationProperties(ResearchProperties.class)
class ResearchDocumentControllerTests {

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("finvera.security.owner.id", () -> "00000000-0000-0000-0000-000000000001");
        registry.add("finvera.security.owner.username", () -> "owner-test");
        registry.add("finvera.security.owner.password-hash", () -> "$2a$04$test");
        registry.add("finvera.research.internal-api-key", () -> "dev-internal-key-change-in-prod");
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResearchDocumentService documentService;

    @MockitoBean
    private RetrievalService retrievalService;

    @Test
    void unauthenticatedRequest_rejectedWith401() throws Exception {
        mockMvc.perform(get("/api/v1/research/documents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitDocument_authenticatedWithCsrf_returns201() throws Exception {
        UUID docId = UUID.randomUUID();
        DocumentResponse response = new DocumentResponse(
                docId, "FPT AR 2025", "FPT", DocumentType.ANNUAL_REPORT, 2025, null, "Source",
                LocalDate.of(2026, 3, 15), IngestionStatus.PENDING, null, Instant.now(), null);

        when(documentService.submitDocument(any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile("file", "fpt.pdf", "application/pdf", "pdf bytes".getBytes());

        mockMvc.perform(multipart("/api/v1/research/documents")
                        .file(file)
                        .param("title", "FPT AR 2025")
                        .param("symbol", "FPT")
                        .param("documentType", "ANNUAL_REPORT")
                        .param("year", "2025")
                        .param("source", "Source")
                        .param("publicationDate", "2026-03-15")
                        .header("Idempotency-Key", "idem-ctrl-1")
                        .with(user("owner-test").roles("OWNER"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(docId.toString()))
                .andExpect(jsonPath("$.title").value("FPT AR 2025"))
                .andExpect(jsonPath("$.ingestionStatus").value("PENDING"));
    }

    @Test
    void listDocuments_authenticated_returns200() throws Exception {
        DocumentPageResponse page = new DocumentPageResponse(List.of(), 0, 20, 0);
        when(documentService.listDocuments(any(), any(), any(), any(), eq(20), eq(0))).thenReturn(page);

        mockMvc.perform(get("/api/v1/research/documents")
                        .with(user("owner-test").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void deleteDocument_authenticatedWithCsrf_returns204() throws Exception {
        UUID docId = UUID.randomUUID();
        doNothing().when(documentService).deleteDocument(docId);

        mockMvc.perform(delete("/api/v1/research/documents/{id}", docId)
                        .with(user("owner-test").roles("OWNER"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void retrievePassages_authenticatedWithCsrf_returns200() throws Exception {
        RetrieveResponse response = new RetrieveResponse(
                List.of(new PassageResponse(
                        SourceType.DOCUMENT,
                        UUID.randomUUID(),
                        "FPT Doc",
                        "Page 1",
                        "Source",
                        LocalDate.of(2026, 3, 15),
                        "Passage excerpt",
                        0.85)),
                Instant.now());

        when(retrievalService.retrievePassages(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/research/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"What is FPT revenue?\"}")
                        .with(user("owner-test").roles("OWNER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passages").isArray())
                .andExpect(jsonPath("$.passages[0].sourceTitle").value("FPT Doc"));
    }
}
