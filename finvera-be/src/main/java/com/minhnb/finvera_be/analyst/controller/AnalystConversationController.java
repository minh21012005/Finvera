package com.minhnb.finvera_be.analyst.controller;

import static com.minhnb.finvera_be.analyst.dto.AnalystConversationDtos.*;

import com.minhnb.finvera_be.analyst.service.AnalystConversationService;
import com.minhnb.finvera_be.portfolio.service.OwnerScopedAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.validation.annotation.Validated;

@RestController
@Validated
@RequestMapping("/api/v1/analyst/conversations")
public class AnalystConversationController {
    private final AnalystConversationService service;
    private final OwnerScopedAccess owners;
    public AnalystConversationController(AnalystConversationService service, OwnerScopedAccess owners) {
        this.service=service; this.owners=owners;
    }

    @GetMapping public ConversationPage list(@RequestParam(required=false) String cursor,
            @RequestParam(defaultValue="20") @Min(1) @Max(50) int limit) {
        return service.list(owners.getAuthenticatedOwnerId(), cursor, limit);
    }
    @PostMapping(value="/ask", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@Valid @RequestBody ConversationAskRequest request) {
        return service.ask(owners.getAuthenticatedOwnerId(), request);
    }
    @GetMapping("/{conversationId}/exchanges")
    public ExchangePage read(@PathVariable UUID conversationId, @RequestParam(required=false) String before,
            @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) {
        return service.read(owners.getAuthenticatedOwnerId(), conversationId, before, limit);
    }
    @PatchMapping("/{conversationId}")
    public ConversationSummary rename(@PathVariable UUID conversationId,
            @Valid @RequestBody RenameConversationRequest request) {
        return service.rename(owners.getAuthenticatedOwnerId(), conversationId, request.title());
    }
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> delete(@PathVariable UUID conversationId) {
        service.delete(owners.getAuthenticatedOwnerId(), conversationId);
        return ResponseEntity.noContent().build();
    }
}
