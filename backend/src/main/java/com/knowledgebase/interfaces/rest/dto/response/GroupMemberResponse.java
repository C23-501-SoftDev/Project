package com.knowledgebase.interfaces.rest.dto.response;

import com.knowledgebase.domain.model.GlobalRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * DTO ответа с данными участника группы.
 * Объединяет запись о членстве с данными пользователя.
 */
@Schema(description = "Участник группы пользователей")
public record GroupMemberResponse(

    @Schema(description = "ID записи о членстве", example = "10")
    Long membershipId,

    @Schema(description = "ID группы", example = "1")
    Long groupId,

    @Schema(description = "ID пользователя", example = "42")
    Long userId,

    @Schema(description = "Логин пользователя", example = "j.doe")
    String login,

    @Schema(description = "Email пользователя", example = "j.doe@example.com")
    String email,

    @Schema(description = "Глобальная роль пользователя", example = "READER")
    GlobalRole role,

    @Schema(description = "Флаг soft-удаления пользователя")
    boolean userDeleted,

    @Schema(description = "Дата добавления в группу")
    LocalDateTime addedAt
) {}
