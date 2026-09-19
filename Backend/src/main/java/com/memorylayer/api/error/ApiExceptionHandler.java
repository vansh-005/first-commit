package com.memorylayer.api.error;

import com.memorylayer.api.dto.ErrorResponse;
import com.memorylayer.api.logging.RequestLoggingInterceptor;
import com.memorylayer.api.observability.StructuredLog;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;

/**
 * Maps application exceptions to the Docs/API.md §6 error envelope. The generic
 * {@code Exception} handler is the safety net for anything unmapped.
 *
 * <p>Phase 7: every handler also emits a structured JSON log line (safe error type + mapped
 * code, never the exception message verbatim — that could echo back request content) carrying
 * the same {@code requestId} returned to the client, correlating client-reported errors with
 * CloudWatch. The id is read from the request attribute {@code RequestLoggingInterceptor} sets,
 * so the client-visible id and the log line's id are always identical; a fresh one is generated
 * only if that attribute is unexpectedly absent.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDocumentNotFound(HttpServletRequest request) {
        String requestId = requestId(request);
        logError(requestId, "DOCUMENT_NOT_FOUND", DocumentNotFoundException.class);
        ErrorResponse body = new ErrorResponse(new ErrorResponse.ErrorBody(
                "DOCUMENT_NOT_FOUND",
                "The requested document does not exist.",
                requestId,
                false));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        logError(requestId, "VALIDATION_ERROR", InvalidRequestException.class);
        ErrorResponse body = new ErrorResponse(new ErrorResponse.ErrorBody(
                "VALIDATION_ERROR",
                ex.getMessage(),
                requestId,
                false));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(RetrievalUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleRetrievalUnavailable(RetrievalUnavailableException ex, HttpServletRequest request) {
        HttpStatus status = ex.isRetryable() ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY;
        String code = ex.isRetryable() ? "RATE_LIMITED" : "UPSTREAM_UNAVAILABLE";
        String requestId = requestId(request);
        logError(requestId, code, RetrievalUnavailableException.class);
        ErrorResponse body = new ErrorResponse(new ErrorResponse.ErrorBody(
                code,
                ex.getMessage(),
                requestId,
                ex.isRetryable()));
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(AskSessionExpiredException.class)
    public ResponseEntity<ErrorResponse> handleAskSessionExpired(HttpServletRequest request) {
        String requestId = requestId(request);
        logError(requestId, "ASK_SESSION_EXPIRED", AskSessionExpiredException.class);
        ErrorResponse body = new ErrorResponse(new ErrorResponse.ErrorBody(
                "ASK_SESSION_EXPIRED",
                "This conversation has expired. Please start a new one.",
                requestId,
                false));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.error("requestId={} event=unhandled_exception", requestId, ex);
        StructuredLog.error("api_error", Map.of(
                "requestId", requestId,
                "code", "INTERNAL_ERROR",
                "errorType", ex.getClass().getSimpleName()));

        ErrorResponse body = new ErrorResponse(new ErrorResponse.ErrorBody(
                "INTERNAL_ERROR",
                "Something went wrong. Please try again.",
                requestId,
                true));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private static void logError(String requestId, String code, Class<? extends Exception> exceptionType) {
        StructuredLog.warn("api_error", Map.of(
                "requestId", requestId,
                "code", code,
                "errorType", exceptionType.getSimpleName()));
    }

    /** Falls back to a fresh id only if {@code RequestLoggingInterceptor} unexpectedly never
     * ran for this request — normal request handling always has one. */
    private static String requestId(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestLoggingInterceptor.REQUEST_ID_ATTR);
        return attribute != null ? attribute.toString() : UUID.randomUUID().toString();
    }
}
