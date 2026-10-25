package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.RagAnswerService;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.rag.RagException;
import com.knowledgebase.interfaces.rest.dto.request.RagAskRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "RAG", description = "Ответы по доступным опубликованным статьям")
public class RagController {
    private final RagAnswerService answerService;

    public RagController(RagAnswerService answerService) {
        this.answerService = answerService;
    }

    @GetMapping("/rag/status")
    @Operation(summary = "Доступность ответов по базе знаний")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("enabled", answerService.isAvailable()));
    }

    @PostMapping("/ask")
    @Operation(summary = "Ответить на вопрос по доступным статьям с указанием источников")
    public ResponseEntity<?> ask(@AuthenticationPrincipal User user,
                                 @Valid @RequestBody RagAskRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(answerService.ask(user.getId(), user.isAdmin(), request.question(), request.spaceIds()));
    }

    @ExceptionHandler(RagException.class)
    public ResponseEntity<?> handleRagError(RagException exception) {
        // Provider exception messages can contain URLs, credentials or document excerpts.
        return switch (exception.getCode()) {
            case DISABLED -> error(HttpStatus.SERVICE_UNAVAILABLE, "DISABLED",
                    "Ответы по базе знаний пока недоступны. Обратитесь к администратору.");
            case INVALID_REQUEST -> error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "Введите вопрос от 1 до 2000 символов и выберите не более 20 пространств.");
            case RATE_LIMITED -> error(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Лимит запросов исчерпан. Попробуйте позже.");
            case PROVIDER_UNAVAILABLE -> error(HttpStatus.BAD_GATEWAY, "PROVIDER_UNAVAILABLE",
                    "Сервис ответов временно недоступен. Попробуйте позже.");
            case SOURCES_CHANGED -> error(HttpStatus.CONFLICT, "SOURCES_CHANGED",
                    "Статьи или права доступа изменились. Задайте вопрос повторно.");
            case INDEX_UNAVAILABLE -> error(HttpStatus.SERVICE_UNAVAILABLE, "INDEX_UNAVAILABLE",
                    "Поиск по статьям временно недоступен. Попробуйте позже.");
        };
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<?> handleInvalidRequest() {
        // Do not echo or log a rejected question through the global validation handler.
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Введите вопрос от 1 до 2000 символов и выберите не более 20 пространств.");
    }

    private ResponseEntity<?> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(Map.of("code", code, "message", message));
    }
}
