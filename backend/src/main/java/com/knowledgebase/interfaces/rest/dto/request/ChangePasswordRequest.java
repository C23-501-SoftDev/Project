package com.knowledgebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO для сброса/изменения пароля (PUT /api/admin/users/{id}/password).
 */
@Schema(description = "Запрос на изменение пароля пользователя")
public record ChangePasswordRequest(

    @Schema(description = "Новый пароль (минимум 6 символов)", example = "newSecurePass456")
    @NotBlank(message = "Пароль не может быть пустым")
    @Size(min = 6, max = 100, message = "Пароль должен содержать от 6 до 100 символов")
    String newPassword
) {}
