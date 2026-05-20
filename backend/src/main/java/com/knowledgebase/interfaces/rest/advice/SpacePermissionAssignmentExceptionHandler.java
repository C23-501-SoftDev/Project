package com.knowledgebase.interfaces.rest.advice;

import com.knowledgebase.domain.exception.InvalidPermissionAssignmentException;
import com.knowledgebase.domain.exception.SpacePermissionNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SpacePermissionAssignmentExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SpacePermissionAssignmentExceptionHandler.class);

    @ExceptionHandler(SpacePermissionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            SpacePermissionNotFoundException ex, HttpServletRequest request) {
        log.warn("Право доступа не найдено: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(InvalidPermissionAssignmentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAssignment(
            InvalidPermissionAssignmentException ex, HttpServletRequest request) {
        log.warn("Некорректное назначение права: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "Unprocessable Entity",
                ex.getMessage(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }
}
