package com.knowledgebase.infrastructure.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Обработчик ошибок 403 Forbidden.
 *
 * Для REST API запросов — возвращает JSON.
 * Для обычных браузерных запросов — редирект на страницу 403 или отображение ошибки.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(JwtAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        log.warn("Доступ запрещён к {}: {}", request.getRequestURI(), accessDeniedException.getMessage());

        if (isAjaxRequest(request) || isApiRequest(request)) {
            sendJsonResponse(response, request);
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Доступ запрещён");
        }
    }

    private boolean isAjaxRequest(HttpServletRequest request) {
        String acceptHeader = request.getHeader("Accept");
        String xRequestedWith = request.getHeader("X-Requested-With");
        return StringUtils.hasText(acceptHeader) && acceptHeader.contains(MediaType.APPLICATION_JSON_VALUE)
                || "XMLHttpRequest".equals(xRequestedWith);
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri.startsWith("/api/");
    }

    private void sendJsonResponse(HttpServletResponse response, HttpServletRequest request) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", 403);
        body.put("error", "Forbidden");
        body.put("message", "Недостаточно прав для выполнения данной операции");
        body.put("path", request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
