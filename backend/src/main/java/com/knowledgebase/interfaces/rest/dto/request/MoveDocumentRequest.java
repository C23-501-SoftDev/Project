package com.knowledgebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "Запрос на перемещение документа")
public record MoveDocumentRequest(
    @Schema(description = "ID целевого пространства")
    Long spaceId,
    @Schema(description = "ID целевого родительского документа")
    Long parentId,
    @PositiveOrZero
    @Schema(description = "Позиция документа среди элементов с тем же родителем (начиная с 0)", example = "2")
    Integer position
) {}
