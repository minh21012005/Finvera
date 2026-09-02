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
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ProblemDetailsAdvice {

    public static final String CORRELATION_ATTRIBUTE = ProblemDetailsAdvice.class.getName() + ".correlationId";
    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ProblemDetail> invalidCredentials(HttpServletRequest request) {
        return response(request, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Authentication failed");
    }

    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestTimeoutException.class)
    void asyncRequestTimeout(HttpServletRequest request, Exception ex) {
        log.warn("Async request timed out: correlationId={} path={}", correlationId(request), request.getRequestURI());
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

    /** Keeps the status a controller chose (e.g. 404 "Unknown owner") instead of the /error round trip. */
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> responseStatus(HttpServletRequest request, ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String title = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return response(request, status, status.name(), title);
    }

    /**
     * Q-49: an unhandled exception used to be forwarded to /error, which sits behind the
     * OWNER rule — so an internal-service caller saw 401 AUTHENTICATION_REQUIRED instead of
     * the 500 that actually happened, and the real cause was only in the server log. Every
     * unhandled failure now becomes a 500 problem with the correlation id and no internals.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unhandled(HttpServletRequest request, Exception ex) {
        log.error("unhandled_request_failure correlationId={} path={} exception={}",
                correlationId(request), request.getRequestURI(), ex.getClass().getName(), ex);
        return response(request, HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_ERROR",
                "The request could not be processed");
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
