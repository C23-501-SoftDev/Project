package com.knowledgebase.interfaces.rest.advice;

import com.knowledgebase.domain.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Глобальный обработчик исключений (Global Exception Handler).
 *
 * Перехватывает все исключения из контроллеров и преобразует их
 * в стандартный ErrorResponse формат.
 *
 * Принцип: контроллеры не занимаются обработкой ошибок — только бизнес-логикой.
 * Все ошибки централизованно обрабатываются здесь.
 *
 * Иерархия HTTP статусов:
 * - 400 Bad Request  → MethodArgumentNotValidException, IllegalArgumentException
 * - 401 Unauthorized → InvalidCredentialsException
 * - 403 Forbidden    → AccessDeniedException (Spring и domain)
 * - 404 Not Found    → UserNotFoundException, SpaceNotFoundException
 * - 409 Conflict     → ConflictException
 * - 500 Internal     → Exception (все необработанные ошибки)
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── 400 Bad Request: ошибки валидации ────────────────────────────────────

    /**
     * Обрабатывает ошибки валидации @Valid аннотаций.
     * Возвращает список всех ошибочных полей.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ErrorResponse.FieldError(
                        error.getField(),
                        error.getRejectedValue(),
                        error.getDefaultMessage()))
                .collect(Collectors.toList());

        ErrorResponse response = ErrorResponse.ofValidation(
                "Ошибка валидации входных данных",
                request.getRequestURI(),
                fieldErrors);

        log.warn("Ошибка валидации для {}: {}", request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        if (isHtmlRequest(request)) {
            return buildHtmlErrorView(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
        }
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
    }

    // ── 401 Unauthorized: ошибки аутентификации ───────────────────────────────

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {
        log.warn("Ошибка аутентификации: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), request);
    }

    @ExceptionHandler(com.knowledgebase.domain.exception.DocumentValidationException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleDocumentValidation(
            com.knowledgebase.domain.exception.DocumentValidationException ex,
            HttpServletRequest request) {
        log.warn("Ошибка валидации документа: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", ex.getMessage(), request);
    }

    @ExceptionHandler(com.knowledgebase.domain.exception.SpaceValidationException.class)
    public ResponseEntity<ErrorResponse> handleSpaceValidation(
            com.knowledgebase.domain.exception.SpaceValidationException ex,
            HttpServletRequest request) {
        log.warn("Ошибка валидации пространства: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", ex.getMessage(), request);
    }

    @ExceptionHandler(com.knowledgebase.domain.exception.AttachmentValidationException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleAttachmentValidation(
            com.knowledgebase.domain.exception.AttachmentValidationException ex,
            HttpServletRequest request) {
        log.warn("Ошибка валидации вложения: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", ex.getMessage(), request);
    }

    // ── 403 Forbidden: ошибки авторизации ────────────────────────────────────

    /**
     * Обрабатывает доменное исключение AccessDeniedException.
     */
    @ExceptionHandler(com.knowledgebase.domain.exception.AccessDeniedException.class)
    public Object handleDomainAccessDenied(
            com.knowledgebase.domain.exception.AccessDeniedException ex,
            HttpServletRequest request) {
        log.warn("Доступ запрещён: {}", ex.getMessage());
        if (isHtmlRequest(request)) {
            return buildHtmlErrorView(HttpStatus.FORBIDDEN, ex.getMessage(), request);
        }
        return buildResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request);
    }

    /**
     * Обрабатывает Spring Security AccessDeniedException.
     * Возникает при нарушении @PreAuthorize условий.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Object handleSpringAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Spring Security: доступ запрещён к {}", request.getRequestURI());
        String msg = "Недостаточно прав для выполнения данной операции";
        if (isHtmlRequest(request)) {
            return buildHtmlErrorView(HttpStatus.FORBIDDEN, msg, request);
        }
        return buildResponse(HttpStatus.FORBIDDEN, "Forbidden", msg, request);
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    @ExceptionHandler(UserNotFoundException.class)
    public Object handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {
        if (isHtmlRequest(request)) {
            return buildHtmlErrorView(HttpStatus.NOT_FOUND, ex.getMessage(), request);
        }
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(AttachmentNotFoundException.class)
    public Object handleAttachmentNotFound(
            AttachmentNotFoundException ex, HttpServletRequest request) {
        if (isHtmlRequest(request)) {
            return buildHtmlErrorView(HttpStatus.NOT_FOUND, ex.getMessage(), request);
        }
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(SpaceNotFoundException.class)
    public Object handleSpaceNotFound(
            SpaceNotFoundException ex, HttpServletRequest request) {
        if (isHtmlRequest(request)) {
            return buildHtmlErrorView(HttpStatus.NOT_FOUND, ex.getMessage(), request);
        }
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        if (isHtmlRequest(request)) {
            return buildHtmlErrorView(HttpStatus.NOT_FOUND, "Ресурс не найден: " + ex.getMessage(), request);
        }
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(GroupNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGroupNotFound(
            GroupNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    // ── 409 Conflict ──────────────────────────────────────────────────────────

    @ExceptionHandler(ConflictException.class)
    public Object handleConflict(
            ConflictException ex, HttpServletRequest request) {
        log.warn("Конфликт данных: {}", ex.getMessage());
        if (isHtmlRequest(request)) {
            return buildHtmlErrorView(HttpStatus.CONFLICT, ex.getMessage(), request);
        }
        return buildResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
    }

    // ── 500 Internal Server Error ─────────────────────────────────────────────

    /**
     * Перехватывает все необработанные исключения.
     * Логирует полный стектрейс, клиенту возвращает детальное сообщение об ошибке.
     */
    @ExceptionHandler(Exception.class)
    public Object handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Полный стектрейс ошибки для {}: ", request.getRequestURI(), ex);
        String msg = "Внутренняя ошибка сервера: " + ex.getMessage();
        if (isHtmlRequest(request)) {
            return buildHtmlErrorView(HttpStatus.INTERNAL_SERVER_ERROR, msg, request);
        }
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", msg, request);
    }

    // ── Вспомогательные методы ───────────────────────────────────────────────

    private boolean isHtmlRequest(HttpServletRequest request) {
        String acceptHeader = request.getHeader("Accept");
        String uri = request.getRequestURI();
        // Если это запрос к API, всегда возвращаем JSON
        if (uri != null && uri.startsWith("/api/")) {
            return false;
        }
        // В противном случае проверяем, запрашивает ли клиент HTML
        return acceptHeader != null && acceptHeader.contains("text/html");
    }

    private ModelAndView buildHtmlErrorView(HttpStatus status, String message, HttpServletRequest request) {
        ModelAndView mav = new ModelAndView("error/error");
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("error", status.getReasonPhrase());
        mav.addObject("message", message);
        mav.addObject("path", request.getRequestURI());
        return mav;
    }

    @ResponseBody
    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error,
                                                         String message, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.of(
                status.value(), error, message, request.getRequestURI());
        return ResponseEntity.status(status).body(response);
    }
}
