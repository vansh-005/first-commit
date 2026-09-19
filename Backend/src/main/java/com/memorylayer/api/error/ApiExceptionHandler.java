package com.memorylayer.api.error;

import com.memorylayer.api.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Maps application exceptions to the Docs/API.md §6 error envelope. The generic
 * {@code Exception} handler is the safety net for anything unmapped.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDocumentNotFound() {
        ErrorResponse body = new ErrorResponse(new ErrorResponse.ErrorBody(
                "DOCUMENT_NOT_FOUND",
                "The requested document does not exist.",
                UUID.randomUUID().toString(),
                false));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex) {
        ErrorResponse body = new ErrorResponse(new ErrorResponse.ErrorBody(
                "VALIDATION_ERROR",
                ex.getMessage(),
                UUID.randomUUID().toString(),
                false));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(RetrievalUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleRetrievalUnavailable(RetrievalUnavailableException ex) {
        HttpStatus status = ex.isRetryable() ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY;
        String code = ex.isRetryable() ? "RATE_LIMITED" : "UPSTREAM_UNAVAILABLE";
        ErrorResponse body = new ErrorResponse(new ErrorResponse.ErrorBody(
                code,
                ex.getMessage(),
                UUID.randomUUID().toString(),
                ex.isRetryable()));
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(AskSessionExpiredException.class)
    public ResponseEntity<ErrorResponse> handleAskSessionExpired() {
        ErrorResponse body = new ErrorResponse(new ErrorResponse.ErrorBody(
                "ASK_SESSION_EXPIRED",
                "This conversation has expired. Please start a new one.",
                UUID.randomUUID().toString(),
                false));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        String requestId = UUID.randomUUID().toString();
        log.error("requestId={} event=unhandled_exception", requestId, ex);

        ErrorResponse body = new ErrorResponse(new ErrorResponse.ErrorBody(
                "INTERNAL_ERROR",
                "Something went wrong. Please try again.",
                requestId,
                true));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
