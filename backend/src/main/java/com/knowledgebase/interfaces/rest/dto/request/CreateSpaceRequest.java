package com.knowledgebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO для создания пространства (POST /api/admin/spaces).
 */
@Schema(description = "Запрос на создание пространства документов")
public record CreateSpaceRequest(

    @Schema(description = "Уникальное название пространства", example = "Backend Development")
    @NotBlank(message = "Название пространства не может быть пустым")
    @Size(min = 2, max = 200, message = "Название должно содержать от 2 до 200 символов")
    String name,

    @Schema(description = "Описание пространства", example = "Документация по бэкенд разработке")
    String description,

    @Schema(description = "ID пользователя-владельца (null = текущий пользователь)")
    Long ownerId
) {}
