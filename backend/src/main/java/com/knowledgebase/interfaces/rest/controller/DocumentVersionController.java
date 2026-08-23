package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.DocumentVersionService;
import com.knowledgebase.domain.model.DiffAlgorithmType;
import com.knowledgebase.interfaces.rest.dto.response.DiffLineResponse;
import com.knowledgebase.interfaces.rest.dto.response.DiffSegmentResponse;
import com.knowledgebase.interfaces.rest.dto.response.DocumentDiffResponse;
import com.knowledgebase.interfaces.rest.dto.response.DocumentVersionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    @Operation(summary = "Сравнить две версии документа", description = "Возвращает diff от from к to с выбранной детализацией")
    public ResponseEntity<DocumentDiffResponse> compareVersions(@PathVariable Long id,
                                                                 @RequestParam String from,
                                                                 @RequestParam String to,
                                                                 @RequestParam(defaultValue = "changed") String context,
                                                                 @RequestParam(required = false) String algorithm) {
        if (!context.equals("changed") && !context.equals("all")) {
            throw new IllegalArgumentException("Параметр context должен быть changed или all");
        }
        var diff = documentVersionService.compareVersions(id, from, to, context.equals("all"),
                DiffAlgorithmType.fromRequest(algorithm));
        var lines = diff.lines().stream()
                .map(line -> new DiffLineResponse(line.type().name(), line.beforeLineNumber(),
                        line.afterLineNumber(), line.content(), line.segments().stream()
                        .map(segment -> new DiffSegmentResponse(segment.type().name(), segment.content()))
                        .toList()))
                .toList();
        return ResponseEntity.ok(new DocumentDiffResponse(diff.documentId(), diff.fromHash(), diff.toHash(),
                diff.algorithm().name(), lines));
    }
}
