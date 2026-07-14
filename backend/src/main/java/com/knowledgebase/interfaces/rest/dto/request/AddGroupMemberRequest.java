package com.knowledgebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * DTO для добавления пользователя в группу
 * (POST /api/admin/groups/{groupId}/members).
 */
@Schema(description = "Запрос на добавление пользователя в группу")
public record AddGroupMemberRequest(

    @Schema(description = "ID пользователя, добавляемого в группу", example = "42")
    @NotNull(message = "ID пользователя обязателен")
    Long userId
) {}
