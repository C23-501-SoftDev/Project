package com.knowledgebase.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final int MAX_REQUEST_ID_LENGTH = 128;

    private final SystemLogger log = SystemLogger.getLogger(HttpRequestLoggingFilter.class, "http");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        long startedAt = System.nanoTime();
        Throwable failure = null;

        MDC.put("request_id", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            int statusCode = response.getStatus();

            if (failure == null) {
                log.info(
                        "HTTP request completed",
                        "http_request",
                        classifyStatus(statusCode),
                        "method", request.getMethod(),
                        "path", request.getRequestURI(),
                        "status_code", statusCode,
                        "duration_ms", durationMs,
                        "client_ip", resolveClientIp(request)
                );
            } else {
                log.error(
                        "HTTP request failed",
                        "http_request",
                        failure,
                        "method", request.getMethod(),
                        "path", request.getRequestURI(),
                        "status_code", statusCode > 0 ? statusCode : 500,
                        "duration_ms", durationMs,
                        "client_ip", resolveClientIp(request)
                );
            }

            MDC.remove("request_id");
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (isSafeRequestId(requestId)) {
            return requestId.trim();
        }
        return UUID.randomUUID().toString();
    }

    private boolean isSafeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.length() > MAX_REQUEST_ID_LENGTH) {
            return false;
        }
        return requestId.chars().noneMatch(ch -> Character.isISOControl(ch) || Character.isWhitespace(ch));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String classifyStatus(int statusCode) {
        if (statusCode >= 500) {
            return "server_error";
        }
        if (statusCode >= 400) {
            return "client_error";
        }
        return "success";
    }
}
