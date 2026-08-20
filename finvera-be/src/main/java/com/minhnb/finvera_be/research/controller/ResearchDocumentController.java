package com.minhnb.finvera_be.research.controller;

import com.minhnb.finvera_be.research.domain.DocumentType;
import com.minhnb.finvera_be.research.dto.DocumentPageResponse;
import com.minhnb.finvera_be.research.dto.DocumentResponse;
import com.minhnb.finvera_be.research.dto.SubmitDocumentCommand;
import com.minhnb.finvera_be.research.service.ResearchDocumentService;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/research/documents")
public class ResearchDocumentController {

    private final ResearchDocumentService documentService;

    public ResearchDocumentController(ResearchDocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> submitDocument(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam("title") String title,
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestParam("documentType") DocumentType documentType,
            @RequestParam("year") int year,
            @RequestParam(value = "quarter", required = false) Integer quarter,
            @RequestParam("source") String source,
            @RequestParam("publicationDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate publicationDate,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "pastedText", required = false) String pastedText) throws IOException {

        byte[] fileBytes = null;
        String originalFilename = null;
        if (file != null && !file.isEmpty()) {
            fileBytes = file.getBytes();
            originalFilename = file.getOriginalFilename();
        }

        SubmitDocumentCommand command = new SubmitDocumentCommand(
                title,
                symbol,
                documentType,
                year,
                quarter,
                source,
                publicationDate,
                fileBytes,
                originalFilename,
                pastedText,
                idempotencyKey);

        DocumentResponse response = documentService.submitDocument(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<DocumentPageResponse> listDocuments(
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestParam(value = "documentType", required = false) DocumentType documentType,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        DocumentPageResponse response = documentService.listDocuments(symbol, documentType, dateFrom, dateTo, limit, offset);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable("documentId") UUID documentId) {
        DocumentResponse response = documentService.getDocument(documentId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable("documentId") UUID documentId) {
        documentService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }
}
