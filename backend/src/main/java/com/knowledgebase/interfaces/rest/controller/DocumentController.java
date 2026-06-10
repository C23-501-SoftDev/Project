package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.DocumentService;
import com.knowledgebase.application.service.PermissionService;
import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.interfaces.rest.dto.request.CreateDocumentRequest;
import com.knowledgebase.interfaces.rest.dto.request.UpdateDocumentRequest;
import com.knowledgebase.interfaces.rest.dto.response.DocumentResponse;
import com.knowledgebase.interfaces.rest.dto.response.PageResponse;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Контроллер управления документами.
 */
@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Управление документами (БД + Git)")
public class DocumentController {

    private final DocumentService documentService;
    private final PermissionService permissionService;
    private final RestDtoMapper mapper;

    private static final int MAX_SEARCH_PAGE_SIZE = 50;

    public DocumentController(DocumentService documentService,
                              PermissionService permissionService,
                              RestDtoMapper mapper) {
        this.documentService = documentService;
        this.permissionService = permissionService;
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
     * POST /api/documents/{id}/restore
     * Восстановить документ из архива.
     */
    @PostMapping("/{id}/restore")
    @PreAuthorize("@permissionService.canWrite(principal.id, principal.isAdmin, @documentService.getDocumentById(#id).spaceId)")
    @Operation(summary = "Восстановить документ", description = "Переводит в статус Published/Draft и перемещает файл из .archive/ в Git")
    public ResponseEntity<Void> restoreDocument(@PathVariable Long id) {
        documentService.restoreDocument(id);
        return ResponseEntity.noContent().build();
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

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Список документов", description = "Возвращает список метаданных всех документов, доступных пользователю")
    public ResponseEntity<?> getDocuments(
            @RequestParam(required = false) Long spaceId,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) java.util.List<String> status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User currentUser) {
        
        List<DocumentResponse> pagedContent;
        long totalElements;

        if (spaceId != null) {
            // Для конкретного пространства проверяем доступ
            if (!permissionService.canRead(currentUser.getId(), currentUser.isAdmin(), spaceId)) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
            }
            if (authorId != null && !currentUser.isAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            pagedContent = documentService.getDocumentsInSpacePaged(spaceId, authorId, includeDeleted, page, size)
                    .stream().map(doc -> mapper.toDocumentResponse(doc, null)).toList();
            totalElements = documentService.countDocumentsInSpace(spaceId, authorId, includeDeleted);
        } else {
            List<Document> all = documentService.getAllAccessibleDocuments(
                    currentUser.getId(), currentUser.isAdmin(), includeDeleted);
            
            // Фильтрация по автору (добавлено)
            if (authorId != null) {
                if (!currentUser.isAdmin()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
                all = all.stream().filter(d -> authorId.equals(d.getAuthorId())).toList();
            }

            List<Document> filteredList = new java.util.ArrayList<>(all);
            
            // Фильтрация по статусам
            if (status != null && !status.isEmpty()) {
                java.util.Set<String> statusSet = new java.util.HashSet<>(status);
                filteredList = filteredList.stream()
                        .filter(doc -> doc.getStatus() != null && statusSet.contains(doc.getStatus().name()))
                        .toList();
            }

            // Фильтрация по поисковому запросу
            if (search != null && !search.isEmpty()) {
                String searchLower = search.toLowerCase();
                filteredList = filteredList.stream()
                        .filter(doc -> doc.getTitle() != null && doc.getTitle().toLowerCase().contains(searchLower))
                        .toList();
            }

            totalElements = filteredList.size();
            int from = Math.min(page * size, (int) totalElements);
            int to = Math.min(from + size, (int) totalElements);
            pagedContent = filteredList.subList(from, to).stream()
                    .map(doc -> mapper.toDocumentResponse(doc, null)).toList();
        }

        int totalPages = (int) Math.ceil((double) totalElements / size);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("content", pagedContent);
        result.put("totalElements", totalElements);
        result.put("totalPages", totalPages);
        result.put("size", size);
        result.put("number", page);

        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/documents/search?q=...
     * Поиск документов по заголовку.
     */
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Поиск документов", description = "Ищет документы по заголовку с учётом прав доступа")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Поиск выполнен успешно"),
        @ApiResponse(responseCode = "400", description = "Некорректная поисковая строка или размер страницы")
    })
    public ResponseEntity<PageResponse<DocumentResponse>> searchDocuments(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User currentUser) {

        if (page < 0) {
            throw new IllegalArgumentException("Номер страницы не может быть отрицательным");
        }
        if (size < 1 || size > MAX_SEARCH_PAGE_SIZE) {
            throw new IllegalArgumentException("Размер страницы должен быть от 1 до " + MAX_SEARCH_PAGE_SIZE);
        }

        var searchPage = documentService.searchDocumentsByTitle(
                q,
                dateFrom,
                dateTo,
                currentUser.getId(),
                currentUser.isAdmin(),
                page,
                size);

        List<DocumentResponse> content = searchPage.getContent().stream()
                .map(document -> mapper.toDocumentResponse(document, null))
                .toList();

        return ResponseEntity.ok(PageResponse.of(content, page, size, searchPage.getTotalElements()));
     * GET /api/documents/authors
     * Возвращает список авторов для фильтрации.
     */
    @GetMapping("/authors")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Список авторов", description = "Возвращает список авторов документов в доступных пространствах")
    public ResponseEntity<List<com.knowledgebase.interfaces.rest.dto.response.UserResponse>> getAuthors(
            @AuthenticationPrincipal User currentUser) {
        
        List<com.knowledgebase.domain.model.User> authors = documentService.findDistinctAuthorsByAccessibleSpaces(currentUser.getId());
        List<com.knowledgebase.interfaces.rest.dto.response.UserResponse> responses = authors.stream()
                .map(mapper::toUserResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
