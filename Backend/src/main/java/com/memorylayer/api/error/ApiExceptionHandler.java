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
 * Safety-net handler so unmapped failures still return the Docs/API.md error envelope
 * instead of a stack trace. Endpoint-specific error codes are added alongside each endpoint
 * in later phases.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

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
