package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.AuditService;
import com.knowledgebase.domain.model.AuditLogEntry;
import com.knowledgebase.interfaces.rest.dto.response.AuditLogResponse;
import com.knowledgebase.interfaces.rest.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Контроллер журнала аудита (US4.1.5 — Логирование действий системы).
 *
 * Доступ только для администраторов:
 * - GET /api/admin/audit — журнал с фильтрами по пользователю, типу действия и датам.
 */
@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Audit", description = "Журнал аудита действий системы (только ADMIN)")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * GET /api/admin/audit
     * Журнал аудита с фильтрацией и пагинацией (только ADMIN).
     */
    @GetMapping
    @Operation(summary = "[ADMIN] Журнал аудита",
               description = "Возвращает записи журнала аудита. Фильтры: userId, actionType, dateFrom, dateTo. "
                           + "Сортировка — по времени, новые записи первыми.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Страница журнала аудита"),
        @ApiResponse(responseCode = "403", description = "Доступ запрещён (не ADMIN)")
    })
    public ResponseEntity<PageResponse<AuditLogResponse>> getAuditLog(
            @Parameter(description = "Фильтр по ID пользователя")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "Фильтр по типу действия, например DOCUMENT_CREATED")
            @RequestParam(required = false) String actionType,
            @Parameter(description = "Нижняя граница времени (ISO), например 2026-07-20T00:00:00")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @Parameter(description = "Верхняя граница времени (ISO)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String normalizedAction = (actionType != null && !actionType.isBlank()) ? actionType : null;

        List<AuditLogResponse> content = auditService
                .getEntries(userId, normalizedAction, dateFrom, dateTo, page, size)
                .stream()
                .map(this::toResponse)
                .toList();
        long total = auditService.countEntries(userId, normalizedAction, dateFrom, dateTo);

        return ResponseEntity.ok(PageResponse.of(content, page, size, total));
    }

    private AuditLogResponse toResponse(AuditLogEntry entry) {
        return new AuditLogResponse(
                entry.getId(),
                entry.getCreatedAt(),
                entry.getUserId(),
                entry.getUserLogin(),
                entry.getActionType(),
                entry.getResourceType(),
                entry.getResourceId(),
                entry.getDetails(),
                entry.getIpAddress());
    }
}
