package com.knowledgebase.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO ответа на отправку тестового письма (US4.3.2, сценарий 2).
 *
 * Отражает статус постановки письма в асинхронную очередь, а не факт
 * доставки: при включённой рассылке письмо отправляется в фоне.
 */
@Schema(description = "Результат отправки тестового email")
public record TestEmailResponse(

    @Schema(description = "Фактический адрес получателя", example = "admin@example.com")
    String recipient,

    @Schema(description = "Письмо передано отправителю (поставлено в очередь)", example = "true")
    boolean queued,

    @Schema(description = "Включена ли реальная рассылка (app.notifications.enabled)",
            example = "false")
    boolean notificationsEnabled
) {}
