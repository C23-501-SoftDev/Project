package com.knowledgebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * DTO добавления пользователя в группу (US4.1.9).
 */
@Schema(description = "Запрос на добавление пользователя в группу")
public record AddGroupMemberRequest(

    @Schema(description = "ID пользователя", example = "2")
    @NotNull(message = "ID пользователя обязателен")
    Long userId
) {
}
