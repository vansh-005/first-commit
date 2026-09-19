package com.memorylayer.api.logging;

import com.memorylayer.api.observability.StructuredLog;
import com.memorylayer.api.security.AuthenticatedUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Emits one human-readable key=value log line plus one structured JSON line (Phase 7,
 * Docs/API.md §27) per request for CloudWatch. */
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger("RequestLog");
    private static final String START_TIME_ATTR = "requestStartTimeMillis";

    /** Public so {@code ApiExceptionHandler} can read back the same id it was assigned here,
     * keeping the client-visible {@code requestId} and this request's structured log lines
     * correlated under one value rather than two independently-generated UUIDs. */
    public static final String REQUEST_ID_ATTR = "requestId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        request.setAttribute(REQUEST_ID_ATTR, UUID.randomUUID().toString());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                 @Nullable Exception ex) {
        long startTime = (long) request.getAttribute(START_TIME_ATTR);
        long durationMs = System.currentTimeMillis() - startTime;
        String requestId = (String) request.getAttribute(REQUEST_ID_ATTR);
        log.info("method={} path={} status={} durationMs={}",
                request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("requestId", requestId);
        fields.put("method", request.getMethod());
        fields.put("path", request.getRequestURI());
        fields.put("status", response.getStatus());
        fields.put("durationMs", durationMs);
        // Best-effort — unauthenticated/public routes (health) have no JWT context to resolve.
        try {
            fields.put("userIdHash", StructuredLog.hashUserId(AuthenticatedUserResolver.resolveUserId(request)));
        } catch (RuntimeException ignored) {
            // No authenticated user on this request — omit the field rather than guess.
        }
        StructuredLog.info("api_request", fields);
    }
}
