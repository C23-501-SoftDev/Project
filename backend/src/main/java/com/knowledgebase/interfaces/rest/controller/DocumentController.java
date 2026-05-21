package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.DocumentService;
import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.interfaces.rest.dto.request.CreateDocumentRequest;
import com.knowledgebase.interfaces.rest.dto.request.UpdateDocumentRequest;
import com.knowledgebase.interfaces.rest.dto.response.DocumentResponse;
import com.knowledgebase.interfaces.rest.mapper.RestDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Контроллер управления документами.
 */
@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Управление документами (БД + Git)")
public class DocumentController {

    private final DocumentService documentService;
    private final RestDtoMapper mapper;

    public DocumentController(DocumentService documentService, RestDtoMapper mapper) {
        this.documentService = documentService;
        this.mapper = mapper;
    }

    /**
     * POST /api/documents
     * Создать новый документ.
     */
    @PostMapping
    @PreAuthorize("@permissionService.canWrite(principal.id, principal.isAdmin, #request.spaceId())")
    @Operation(summary = "Создать документ", description = "Создаёт метаданные в БД и файл в Git")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Документ создан"),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав для записи в пространство")
    })
    public ResponseEntity<DocumentResponse> createDocument(
            @Valid @RequestBody CreateDocumentRequest request,
            @AuthenticationPrincipal User currentUser) {

        Document document = documentService.createDocument(
                request.title(), 
                request.content(), 
                request.spaceId(), 
                request.parentId(),
                currentUser.getId(),
                request.templateId()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toDocumentResponse(document, request.content()));
    }

    /**
     * GET /api/documents/{id}
     * Получить документ с содержимым.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@permissionService.canRead(principal.id, principal.isAdmin, @documentService.getDocumentById(#id).spaceId)")
    @Operation(summary = "Получить документ", description = "Возвращает метаданные из БД и содержимое из Git")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Документ найден"),
        @ApiResponse(responseCode = "404", description = "Документ не найден")
    })
    public ResponseEntity<DocumentResponse> getDocument(
            @Parameter(description = "ID документа") @PathVariable Long id) {
        
        Document document = documentService.getDocumentById(id);
        String content = documentService.getDocumentContent(document);
        return ResponseEntity.ok(mapper.toDocumentResponse(document, content));
    }

    /**
     * PUT /api/documents/{id}
     * Обновить метаданные или контент.
     */
    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.canWrite(principal.id, principal.isAdmin, @documentService.getDocumentById(#id).spaceId)")
    @Operation(summary = "Обновить документ", description = "Обновляет метаданные в БД и создаёт новый коммит в Git")
    public ResponseEntity<DocumentResponse> updateDocument(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDocumentRequest request,
            @AuthenticationPrincipal User currentUser) {

        Document document = documentService.updateDocument(
                id, request.title(), request.content(), request.status(), request.parentId(), currentUser.getId());
        
        String content = request.content() != null ? request.content() : documentService.getDocumentContent(document);
        return ResponseEntity.ok(mapper.toDocumentResponse(document, content));
    }

    /**
     * DELETE /api/documents/{id}
     * Удалить документ (архивация).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.canWrite(principal.id, principal.isAdmin, @documentService.getDocumentById(#id).spaceId)")
    @Operation(summary = "Удалить документ", description = "Переводит в статус Deleted и перемещает файл в .archive/ в Git")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/documents
     * Список всех доступных пользователю документов.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Список документов", description = "Возвращает список метаданных всех документов, доступных пользователю")
    public ResponseEntity<List<DocumentResponse>> getDocuments(
            @RequestParam(required = false) Long spaceId,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @AuthenticationPrincipal User currentUser) {
        
        List<Document> documents;
        if (spaceId != null) {
            // Для конкретного пространства проверяем доступ
            if (!currentUser.isAdmin() && !com.knowledgebase.application.ApplicationContextHolder.getBean(com.knowledgebase.application.service.PermissionService.class).canRead(currentUser.getId(), currentUser.isAdmin(), spaceId)) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
            }
            documents = documentService.getDocumentsInSpace(spaceId, includeDeleted);
        } else {
            // Получить все документы, доступные пользователю (через сервис)
            documents = documentService.getAllAccessibleDocuments(currentUser.getId(), currentUser.isAdmin(), includeDeleted);
        }



        List<DocumentResponse> response = documents.stream()
                .map(doc -> mapper.toDocumentResponse(doc, null))
                .toList();
        return ResponseEntity.ok(response);
    }
}
