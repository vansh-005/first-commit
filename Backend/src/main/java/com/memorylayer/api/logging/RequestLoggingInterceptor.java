package com.memorylayer.api.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

/** Emits one structured key=value log line per request for CloudWatch. */
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger("RequestLog");
    private static final String START_TIME_ATTR = "requestStartTimeMillis";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                 @Nullable Exception ex) {
        long startTime = (long) request.getAttribute(START_TIME_ATTR);
        long durationMs = System.currentTimeMillis() - startTime;
        log.info("method={} path={} status={} durationMs={}",
                request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
    }
}
