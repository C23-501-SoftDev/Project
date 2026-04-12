package com.knowledgebase.infrastructure.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Обработчик ошибок 401 Unauthorized.
 *
 * Для REST API запросов (Accept: application/json или /api/**) — возвращает JSON.
 * Для обычных браузерных запросов — редирект на страницу /login.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);
    private static final String LOGIN_URL = "/login";

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        log.warn("Неавторизованный запрос к {}: {}", request.getRequestURI(), authException.getMessage());

        if (isAjaxRequest(request) || isApiRequest(request)) {
            sendJsonResponse(response, request);
        } else {
            response.sendRedirect(LOGIN_URL);
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
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", 401);
        body.put("error", "Unauthorized");
        body.put("message", "Требуется аутентификация. Выполните вход через /login");
        body.put("path", request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
