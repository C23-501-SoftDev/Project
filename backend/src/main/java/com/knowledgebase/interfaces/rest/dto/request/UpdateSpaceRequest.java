package com.knowledgebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Запрос на обновление пространства.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос на обновление пространства")
public class UpdateSpaceRequest {

    @Schema(description = "Название пространства", example = "Маркетинг")
    @NotBlank(message = "Название не может быть пустым")
    @Size(max = 100, message = "Название не может быть длиннее 100 символов")
    private String name;

    @Schema(description = "Описание пространства", example = "Все документы отдела маркетинга")
    @Size(max = 500, message = "Описание не может быть длиннее 500 символов")
    private String description;

    @Schema(description = "ID владельца", example = "1")
    @NotNull(message = "ID владельца обязателен")
    private Long ownerId;
}
