package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.AttachmentService;
import com.knowledgebase.domain.model.Attachment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * REST-контроллер скачивания вложений документов.
 */
@RestController
@RequestMapping("/api/attachments")
@Tag(name = "Attachment downloads", description = "Скачивание вложений документов")
public class AttachmentDownloadController {

    private final AttachmentService attachmentService;

    public AttachmentDownloadController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping("/{attachmentId}/download")
    @PreAuthorize("@attachmentService.canDownload(principal.id, principal.isAdmin, #attachmentId)")
    @Operation(summary = "Скачать вложение", description = "Возвращает файл вложения с проверкой прав чтения пространства")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Файл найден"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Вложение не найдено")
    })
    public ResponseEntity<org.springframework.core.io.Resource> downloadAttachment(@PathVariable Long attachmentId) {
        AttachmentService.AttachmentDownloadData downloadData = attachmentService.downloadAttachment(attachmentId);
        Attachment attachment = downloadData.attachment();

        MediaType contentType = resolveContentType(attachment.getContentType());
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(attachment.getFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.status(HttpStatus.OK)
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentLength(attachment.getSizeBytes())
                .body(downloadData.resource());
    }

    private MediaType resolveContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
