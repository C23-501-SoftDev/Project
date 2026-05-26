package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.AttachmentService;
import com.knowledgebase.application.service.DocumentService;
import com.knowledgebase.domain.model.Attachment;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.interfaces.rest.dto.response.AttachmentResponse;
import com.knowledgebase.interfaces.rest.mapper.RestDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST-контроллер управления вложениями документов.
 */
@RestController
@RequestMapping("/api/documents/{documentId}/attachments")
@Tag(name = "Attachments", description = "Управление вложениями документов")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final DocumentService documentService;
    private final RestDtoMapper mapper;

    public AttachmentController(AttachmentService attachmentService,
                                DocumentService documentService,
                                RestDtoMapper mapper) {
        this.attachmentService = attachmentService;
        this.documentService = documentService;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize("@permissionService.canRead(principal.id, principal.isAdmin, @documentService.getDocumentById(#documentId).spaceId)")
    @Operation(summary = "Список вложений", description = "Возвращает список файлов, прикреплённых к документу")
    public ResponseEntity<List<AttachmentResponse>> getAttachments(@PathVariable Long documentId) {
        List<AttachmentResponse> responses = attachmentService.getAttachmentsForDocument(documentId)
                .stream()
                .map(mapper::toAttachmentResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionService.canWrite(principal.id, principal.isAdmin, @documentService.getDocumentById(#documentId).spaceId)")
    @Operation(summary = "Загрузить вложения", description = "Сохраняет файл(ы) документа в blob-хранилище")
    public ResponseEntity<List<AttachmentResponse>> uploadAttachments(
            @PathVariable Long documentId,
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal User currentUser) {

        List<AttachmentResponse> responses = attachmentService.uploadAttachments(documentId, files, currentUser.getId())
                .stream()
                .map(mapper::toAttachmentResponse)
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("@permissionService.canWrite(principal.id, principal.isAdmin, @documentService.getDocumentById(#documentId).spaceId)")
    @Operation(summary = "Удалить вложение", description = "Удаляет файл из blob-хранилища и метаданные из БД")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long documentId,
                                                 @Parameter(description = "ID вложения") @PathVariable Long attachmentId) {
        attachmentService.deleteAttachment(documentId, attachmentId);
        return ResponseEntity.noContent().build();
    }
}
