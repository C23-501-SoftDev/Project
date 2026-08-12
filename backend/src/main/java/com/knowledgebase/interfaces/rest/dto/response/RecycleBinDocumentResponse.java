package com.knowledgebase.interfaces.rest.dto.response;

import java.time.LocalDateTime;

/**
 * DTO для элемента административной корзины документов.
 */
public record RecycleBinDocumentResponse(
        Long id,
        String title,
        String spaceName,
        String authorLogin,
        String previousStatus,
        LocalDateTime deletedAt
) {}
