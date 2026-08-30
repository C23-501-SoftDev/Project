package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.AiTextService;
import com.knowledgebase.interfaces.rest.dto.request.AiTransformRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Контроллер нейроассистента редактирования текста.
 *
 * Доступен всем аутентифицированным пользователям (кнопка ассистента появляется
 * на страницах создания/редактирования документа).
 *
 * - GET  /api/ai/status    — включён ли ассистент и список доступных действий
 * - POST /api/ai/transform — преобразовать текст по выбранному типу или промпту
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI Assistant", description = "Нейроассистент редактирования текста (routerai)")
public class AiController {

    private final AiTextService aiTextService;

    public AiController(AiTextService aiTextService) {
        this.aiTextService = aiTextService;
    }

    /**
     * GET /api/ai/status
     * Статус ассистента + список доступных действий (для фронтенда).
     */
    @GetMapping("/status")
    @Operation(summary = "Статус нейроассистента",
               description = "Возвращает { enabled, actions[] }. Фронтенд скрывает кнопку, если enabled=false.")
    public ResponseEntity<?> status() {
        boolean enabled = aiTextService.isConfigured();
        List<String> actions = enabled ? aiTextService.availableActions() : List.of();
        return ResponseEntity.ok(Map.of("enabled", enabled, "actions", actions));
    }

    /**
     * POST /api/ai/transform
     * Преобразует текст по выбранному типу или пользовательскому промпту.
     */
    @PostMapping("/transform")
    @Operation(summary = "Преобразовать текст",
               description = "Принимает { text, action } или { text, prompt } и возвращает { result } — переписанный текст.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Текст преобразован"),
        @ApiResponse(responseCode = "400", description = "Пустой текст, неизвестное действие или некорректный промпт"),
        @ApiResponse(responseCode = "503", description = "Нейроассистент не настроен"),
        @ApiResponse(responseCode = "502", description = "Ошибка обращения к нейросети")
    })
    public ResponseEntity<?> transform(@Valid @RequestBody AiTransformRequest request) {
        if (request.hasAction() == request.hasPrompt()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Укажите либо тип преобразования, либо пользовательский промпт"));
        }
        if (!aiTextService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Нейроассистент не настроен: задайте AI_ENABLED=true и AI_API_KEY"));
        }
        try {
            String result = aiTextService.transform(request.text(), request.action(), request.prompt());
            return ResponseEntity.ok(Map.of("result", result));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (AiTextService.AiServiceException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Ошибка нейросети: " + ex.getMessage()));
        }
    }
}
