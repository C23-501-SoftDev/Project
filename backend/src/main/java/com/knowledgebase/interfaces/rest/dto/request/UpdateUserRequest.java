package com.knowledgebase.interfaces.rest.dto.request;

import com.knowledgebase.domain.model.GlobalRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * DTO для обновления пользователя (PUT /api/admin/users/{id}).
 * Пароль не обновляется этим эндпоинтом — используйте PUT /api/admin/users/{id}/password.
 * Все поля опциональны — передавайте только те, что нужно изменить.
 */
@Schema(description = "Запрос на обновление пользователя (пароль не изменяется)")
public record UpdateUserRequest(

    @Schema(description = "Новый логин (null = без изменений)", example = "jane.doe")
    @Size(min = 3, max = 100, message = "Логин должен содержать от 3 до 100 символов")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
             message = "Логин может содержать только буквы, цифры, точки, дефисы и подчёркивания")
    String login,

    @Schema(description = "Новый email (null = без изменений)", example = "jane.doe@example.com")
    @Email(message = "Некорректный формат email")
    @Size(max = 255, message = "Email не может превышать 255 символов")
    String email,

    @Schema(description = "Новая роль (null = без изменений)", example = "READER")
    GlobalRole role
) {}
