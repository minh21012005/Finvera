package com.minhnb.finvera_be.shared.api;

import com.minhnb.finvera_be.auth.service.LoginThrottle.LoginRateLimitedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProblemDetailsAdvice {

    public static final String CORRELATION_ATTRIBUTE = ProblemDetailsAdvice.class.getName() + ".correlationId";

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ProblemDetail> invalidCredentials(HttpServletRequest request) {
        return response(request, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Authentication failed");
    }

    @ExceptionHandler(LoginRateLimitedException.class)
    ResponseEntity<ProblemDetail> loginRateLimited(HttpServletRequest request) {
        return response(request, HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED", "Try again later");
    }

    @ExceptionHandler(ProviderAuthRequiredException.class)
    ResponseEntity<ProblemDetail> providerAuthRequired(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_GATEWAY, "PROVIDER_AUTH_REQUIRED",
                "Live provider authentication is unavailable");
    }

    @ExceptionHandler(com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.PortfolioNotFoundException.class)
    ResponseEntity<ProblemDetail> portfolioNotFound(HttpServletRequest request, Exception ex) {
        return response(request, HttpStatus.NOT_FOUND, "PORTFOLIO_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.TransactionNotFoundException.class)
    ResponseEntity<ProblemDetail> transactionNotFound(HttpServletRequest request, Exception ex) {
        return response(request, HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.WatchlistNotFoundException.class)
    ResponseEntity<ProblemDetail> watchlistNotFound(HttpServletRequest request, Exception ex) {
        return response(request, HttpStatus.NOT_FOUND, "WATCHLIST_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.DuplicatePortfolioNameException.class)
    ResponseEntity<ProblemDetail> duplicatePortfolioName(HttpServletRequest request, Exception ex) {
        return response(request, HttpStatus.CONFLICT, "DUPLICATE_PORTFOLIO_NAME", ex.getMessage());
    }

    @ExceptionHandler(com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.DuplicateWatchlistNameException.class)
    ResponseEntity<ProblemDetail> duplicateWatchlistName(HttpServletRequest request, Exception ex) {
        return response(request, HttpStatus.CONFLICT, "DUPLICATE_WATCHLIST_NAME", ex.getMessage());
    }

    @ExceptionHandler(com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.UnsupportedInstrumentException.class)
    ResponseEntity<ProblemDetail> unsupportedInstrument(HttpServletRequest request, Exception ex) {
        return response(request, HttpStatus.BAD_REQUEST, "UNSUPPORTED_INSTRUMENT", ex.getMessage());
    }

    @ExceptionHandler(com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.DuplicateSubmissionException.class)
    ResponseEntity<ProblemDetail> duplicateSubmission(HttpServletRequest request, com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.DuplicateSubmissionException ex) {
        return response(request, HttpStatus.CONFLICT, "DUPLICATE_SUBMISSION",
                "Original transaction id: " + ex.getExistingTransactionId());
    }

    @ExceptionHandler(com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioValidationException.class)
    ResponseEntity<ProblemDetail> portfolioValidation(HttpServletRequest request, com.minhnb.finvera_be.portfolio.domain.analytics.PortfolioValidationException ex) {
        return response(request, HttpStatus.CONFLICT, ex.getReasonCode().name(), ex.getMessage());
    }

    @ExceptionHandler(com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.PeriodTooLongException.class)
    ResponseEntity<ProblemDetail> periodTooLong(HttpServletRequest request, com.minhnb.finvera_be.portfolio.service.PortfolioExceptions.PeriodTooLongException ex) {
        return response(request, HttpStatus.UNPROCESSABLE_ENTITY, "PERIOD_TOO_LONG", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    ResponseEntity<ProblemDetail> missingHeader(HttpServletRequest request, Exception ex) {
        return response(request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidRequest(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request validation failed");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalidArgument(HttpServletRequest request, Exception ex) {
        return response(request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(com.minhnb.finvera_be.research.service.ResearchExceptions.DocumentNotFoundException.class)
    ResponseEntity<ProblemDetail> documentNotFound(HttpServletRequest request, Exception ex) {
        return response(request, HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(com.minhnb.finvera_be.research.service.ResearchExceptions.NewsArticleNotFoundException.class)
    ResponseEntity<ProblemDetail> newsArticleNotFound(HttpServletRequest request, Exception ex) {
        return response(request, HttpStatus.NOT_FOUND, "NEWS_ARTICLE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(com.minhnb.finvera_be.research.service.ResearchExceptions.DuplicateSubmissionException.class)
    ResponseEntity<ProblemDetail> duplicateResearchSubmission(HttpServletRequest request, com.minhnb.finvera_be.research.service.ResearchExceptions.DuplicateSubmissionException ex) {
        return response(request, HttpStatus.CONFLICT, "DUPLICATE_SUBMISSION",
                "Original item id: " + ex.getExistingItemId());
    }

    @ExceptionHandler(com.minhnb.finvera_be.research.service.ResearchExceptions.RetrievalUnavailableException.class)
    ResponseEntity<ProblemDetail> retrievalUnavailable(HttpServletRequest request, Exception ex) {
        return response(request, HttpStatus.SERVICE_UNAVAILABLE, "RETRIEVAL_UNAVAILABLE", ex.getMessage());
    }

    @ExceptionHandler(com.minhnb.finvera_be.research.service.ResearchExceptions.VectorCleanupFailedException.class)
    ResponseEntity<ProblemDetail> vectorCleanupFailed(HttpServletRequest request, Exception ex) {
        return response(request, HttpStatus.BAD_GATEWAY, "VECTOR_CLEANUP_FAILED", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> dataIntegrityViolation(
            HttpServletRequest request, org.springframework.dao.DataIntegrityViolationException ex) {
        if (isResearchIdempotencyConstraintViolation(ex)) {
            // Same real-world situation as the app-level check-then-create idempotency check
            // (ResearchExceptions.DuplicateSubmissionException) — just lost the race to it. Report it
            // with the same reason code so callers see one consistent outcome regardless of timing.
            return response(request, HttpStatus.CONFLICT, "DUPLICATE_SUBMISSION",
                    "Duplicate submission for the same idempotency key");
        }
        return response(request, HttpStatus.CONFLICT, "CONFLICT",
                "The request conflicts with existing data (e.g. a concurrent duplicate submission)");
    }

    private static boolean isResearchIdempotencyConstraintViolation(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && (message.contains("idx_rd_owner_idempotency") || message.contains("idx_na_owner_idempotency"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static void write(
            HttpServletResponse response,
            HttpServletRequest request,
            int status,
            String code,
            String title) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        String correlationId = correlationId(request);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title
                + "\",\"status\":" + status + ",\"code\":\"" + code
                + "\",\"reasonCode\":\"" + code
                + "\",\"correlationId\":\"" + correlationId + "\"}");
    }

    private static ResponseEntity<ProblemDetail> response(
            HttpServletRequest request,
            HttpStatus status,
            String code,
            String title) {
        var problem = ProblemDetail.forStatusAndDetail(status, title);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("reasonCode", code);
        problem.setProperty("correlationId", correlationId(request));
        return ResponseEntity.status(status).body(problem);
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CORRELATION_ATTRIBUTE);
        return value instanceof String correlation ? correlation : UUID.randomUUID().toString();
    }

    public static final class ProviderAuthRequiredException extends RuntimeException {
        public ProviderAuthRequiredException() {
            super("Provider authentication is required");
        }
    }
}
