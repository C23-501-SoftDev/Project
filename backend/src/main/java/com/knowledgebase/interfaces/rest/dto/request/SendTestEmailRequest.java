package com.knowledgebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * DTO для отправки тестового письма (POST /api/admin/notifications/test).
 * Доступно только для ADMIN (US4.3.2, сценарий 2).
 *
 * Поле {@code recipient} необязательно: если не задано, письмо уходит на
 * адрес администратора из настройки {@code app.notifications.admin-email}.
 */
@Schema(description = "Запрос на отправку тестового email")
public record SendTestEmailRequest(

    @Schema(description = "Адрес получателя (опционально; по умолчанию — admin-email)",
            example = "admin@example.com")
    @Email(message = "Некорректный формат email")
    @Size(max = 255, message = "Email не может превышать 255 символов")
    String recipient
) {}
