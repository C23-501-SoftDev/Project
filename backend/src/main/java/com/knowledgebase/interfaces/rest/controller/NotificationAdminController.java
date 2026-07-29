package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.NotificationService;
import com.knowledgebase.application.service.NotificationService.TestEmailResult;
import com.knowledgebase.interfaces.rest.advice.ErrorResponse;
import com.knowledgebase.interfaces.rest.dto.request.SendTestEmailRequest;
import com.knowledgebase.interfaces.rest.dto.response.TestEmailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Контроллер панели администратора — системные уведомления (US4.3.2).
 *
 * Позволяет администратору проверить настройки SMTP, отправив тестовое письмо.
 * Все эндпоинты доступны только для ADMIN (двойная защита: SecurityConfig +
 * {@link PreAuthorize}).
 *
 * Префикс: /api/admin/notifications
 */
@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Notifications", description = "Системные уведомления (только для ADMIN)")
public class NotificationAdminController {

    private final NotificationService notificationService;

    public NotificationAdminController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * POST /api/admin/notifications/test
     * Отправляет тестовое письмо для проверки параметров SMTP.
     *
     * Отправка асинхронна, поэтому возвращается 202 Accepted: тело ответа
     * отражает факт постановки письма в очередь, а не доставку. Тело запроса
     * необязательно — без него письмо уходит на app.notifications.admin-email.
     */
    @PostMapping("/test")
    @Operation(summary = "Отправить тестовое письмо",
               description = "Ставит тестовое письмо в асинхронную очередь для проверки SMTP. "
                       + "Если получатель не указан, используется admin-email из настроек.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Письмо поставлено в очередь",
            content = @Content(schema = @Schema(implementation = TestEmailResponse.class))),
        @ApiResponse(responseCode = "400", description = "Ошибка валидации (некорректный email)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Доступ запрещён",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TestEmailResponse> sendTestEmail(
            @Valid @RequestBody(required = false) SendTestEmailRequest request) {
        String recipient = request != null ? request.recipient() : null;
        TestEmailResult result = notificationService.sendTestEmail(recipient);
        TestEmailResponse body = new TestEmailResponse(
                result.recipient(), result.queued(), result.notificationsEnabled());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }
}
