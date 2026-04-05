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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── 400 Bad Request: ошибки валидации ────────────────────────────────────

    /**
     * Обрабатывает ошибки валидации @Valid аннотаций.
     * Возвращает список всех ошибочных полей.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
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
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
    }

    // ── 401 Unauthorized: ошибки аутентификации ───────────────────────────────

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {
        log.warn("Ошибка аутентификации: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), request);
    }

    // ── 403 Forbidden: ошибки авторизации ────────────────────────────────────

    /**
     * Обрабатывает доменное исключение AccessDeniedException.
     */
    @ExceptionHandler(com.knowledgebase.domain.exception.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleDomainAccessDenied(
            com.knowledgebase.domain.exception.AccessDeniedException ex,
            HttpServletRequest request) {
        log.warn("Доступ запрещён: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request);
    }

    /**
     * Обрабатывает Spring Security AccessDeniedException.
     * Возникает при нарушении @PreAuthorize условий.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleSpringAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Spring Security: доступ запрещён к {}", request.getRequestURI());
        return buildResponse(HttpStatus.FORBIDDEN, "Forbidden",
                "Недостаточно прав для выполнения данной операции", request);
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(SpaceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSpaceNotFound(
            SpaceNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    // ── 409 Conflict ──────────────────────────────────────────────────────────

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            ConflictException ex, HttpServletRequest request) {
        log.warn("Конфликт данных: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
    }

    // ── 500 Internal Server Error ─────────────────────────────────────────────

    /**
     * Перехватывает все необработанные исключения.
     * Логирует полный стектрейс, клиенту возвращает общее сообщение.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Необработанное исключение для {}: ", request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "Внутренняя ошибка сервера. Обратитесь к администратору.", request);
    }

    // ── Вспомогательный метод ─────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error,
                                                         String message, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.of(
                status.value(), error, message, request.getRequestURI());
        return ResponseEntity.status(status).body(response);
    }
}
