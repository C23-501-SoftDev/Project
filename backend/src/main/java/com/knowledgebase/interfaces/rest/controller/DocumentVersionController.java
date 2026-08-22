package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.DocumentVersionService;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.interfaces.rest.dto.response.DiffLineResponse;
import com.knowledgebase.interfaces.rest.dto.response.DocumentDiffResponse;
import com.knowledgebase.interfaces.rest.dto.response.DocumentVersionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Document versions", description = "Просмотр и сравнение сохранённых Git-версий документов")
public class DocumentVersionController {
    private final DocumentVersionService documentVersionService;

    public DocumentVersionController(DocumentVersionService documentVersionService) {
        this.documentVersionService = documentVersionService;
    }

    @GetMapping("/api/documents/{id}/versions")
    @PreAuthorize("@permissionService.canRead(principal.id, principal.isAdmin, @documentService.getDocumentById(#id).spaceId)")
    @Operation(summary = "Получить историю версий документа")
    public ResponseEntity<List<DocumentVersionResponse>> listVersions(@PathVariable Long id) {
        var versions = documentVersionService.listVersions(id).stream()
                .map(version -> new DocumentVersionResponse(version.gitHash(), version.comment(), version.createdAt()))
                .toList();
        return ResponseEntity.ok(versions);
    }

    @GetMapping("/api/documents/{id}/diff")
    @PreAuthorize("@permissionService.canRead(principal.id, principal.isAdmin, @documentService.getDocumentById(#id).spaceId)")
    @Operation(summary = "Сравнить две версии документа", description = "Возвращает построчный Git diff от from к to")
    public ResponseEntity<DocumentDiffResponse> compareVersions(@PathVariable Long id,
                                                                 @RequestParam String from,
                                                                 @RequestParam String to,
                                                                 @RequestParam(defaultValue = "changed") String context) {
        if (!context.equals("changed") && !context.equals("all")) {
            throw new IllegalArgumentException("Параметр context должен быть changed или all");
        }
        var diff = documentVersionService.compareVersions(id, from, to, context.equals("all"));
        var lines = diff.lines().stream()
                .map(line -> new DiffLineResponse(line.type().name(), line.beforeLineNumber(),
                        line.afterLineNumber(), line.content()))
                .toList();
        return ResponseEntity.ok(new DocumentDiffResponse(diff.documentId(), diff.fromHash(), diff.toHash(), lines));
    }

    @PostMapping("/api/documents/{id}/versions/{gitHash}/restore")
    @PreAuthorize("@permissionService.canWrite(principal.id, principal.isAdmin, @documentService.getDocumentById(#id).spaceId)")
    @Operation(summary = "Восстановить версию документа",
            description = "Создаёт новый Git-снимок с текстом выбранной зарегистрированной версии")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Версия восстановлена как новый снимок"),
            @ApiResponse(responseCode = "400", description = "Некорректный SHA версии"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав на редактирование"),
            @ApiResponse(responseCode = "404", description = "Документ или его версия не найдены")
    })
    public ResponseEntity<DocumentVersionResponse> restoreVersion(@PathVariable Long id,
                                                                    @PathVariable String gitHash,
                                                                    @AuthenticationPrincipal User currentUser) {
        var version = documentVersionService.restoreVersion(id, gitHash, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DocumentVersionResponse(version.gitHash(), version.comment(), version.createdAt()));
    }
}
