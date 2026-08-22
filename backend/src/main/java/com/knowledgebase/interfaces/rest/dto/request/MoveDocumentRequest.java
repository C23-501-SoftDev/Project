package com.knowledgebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Запрос на перемещение документа")
public record MoveDocumentRequest(
    @Schema(description = "ID целевого пространства")
    Long spaceId,
    @Schema(description = "ID целевого родительского документа")
    Long parentId
) {}
