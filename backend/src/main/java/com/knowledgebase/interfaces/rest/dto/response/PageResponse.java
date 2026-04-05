package com.knowledgebase.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Обёртка для списков с пагинацией.
 * Используется во всех эндпоинтах, возвращающих списки.
 *
 * @param <T> тип элемента списка
 */
@Schema(description = "Страница списка с метаинформацией пагинации")
public record PageResponse<T>(

    @Schema(description = "Список элементов текущей страницы")
    List<T> content,

    @Schema(description = "Номер текущей страницы (0-based)", example = "0")
    int page,

    @Schema(description = "Размер страницы", example = "20")
    int size,

    @Schema(description = "Общее количество элементов", example = "100")
    long totalElements,

    @Schema(description = "Общее количество страниц", example = "5")
    int totalPages,

    @Schema(description = "Является ли текущая страница последней", example = "false")
    boolean last
) {
    /**
     * Вспомогательный конструктор для создания PageResponse.
     */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / size);
        boolean last = (page + 1) >= totalPages;
        return new PageResponse<>(content, page, size, totalElements, totalPages, last);
    }
}
