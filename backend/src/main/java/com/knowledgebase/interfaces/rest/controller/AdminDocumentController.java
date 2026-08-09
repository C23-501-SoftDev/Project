package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.DocumentService;
import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.repository.SpaceRepository;
import com.knowledgebase.domain.repository.UserRepository;
import com.knowledgebase.interfaces.rest.dto.response.RecycleBinDocumentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Контроллер панели администратора — управление корзиной документов.
 * Доступен только для ADMIN.
 */
@RestController
@RequestMapping("/api/admin/documents")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Documents Recycle Bin", description = "Административная корзина документов (только для ADMIN)")
public class AdminDocumentController {

    private final DocumentService documentService;
    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;

    public AdminDocumentController(DocumentService documentService,
                                  SpaceRepository spaceRepository,
                                  UserRepository userRepository) {
        this.documentService = documentService;
        this.spaceRepository = spaceRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/recycle-bin")
    @Operation(summary = "Список удаленных документов в корзине", description = "Возвращает список документов со статусом DELETED с метаданными.")
    public ResponseEntity<List<RecycleBinDocumentResponse>> getRecycleBinDocuments() {
        List<Document> deletedDocs = documentService.getRecycleBinDocuments();

        List<RecycleBinDocumentResponse> response = deletedDocs.stream().map(doc -> {
            String spaceName = spaceRepository.findByIdIncludingDeleted(doc.getSpaceId())
                    .map(s -> s.getName())
                    .orElse("Неизвестно");

            String authorLogin = userRepository.findById(doc.getAuthorId())
                    .map(u -> u.getLogin())
                    .orElse("Система");

            String prevStatus = doc.getPreviousStatus() != null ? doc.getPreviousStatus().name() : "DRAFT";
            return new RecycleBinDocumentResponse(
                    doc.getId(),
                    doc.getTitle(),
                    spaceName,
                    authorLogin,
                    prevStatus,
                    doc.getUpdatedAt()
            );
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Восстановить документ из корзины", description = "Переводит документ из DELETED в предыдущий статус (Draft/Published).")
    public ResponseEntity<Void> restoreDocument(@PathVariable Long id) {
        documentService.restoreDocument(id);
        return ResponseEntity.noContent().build();
    }
}

