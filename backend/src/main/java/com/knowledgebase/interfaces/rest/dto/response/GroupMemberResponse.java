package com.knowledgebase.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * DTO участника группы (US4.1.9).
 */
@Schema(description = "Участник группы пользователей")
public record GroupMemberResponse(

    @Schema(description = "ID пользователя", example = "2")
    Long userId,

    @Schema(description = "Логин пользователя", example = "analyst1")
    String login,

    @Schema(description = "Email пользователя", example = "analyst1@example.com")
    String email,

    @Schema(description = "Дата добавления в группу")
    LocalDateTime addedAt
) {
}
