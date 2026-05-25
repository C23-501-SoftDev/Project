package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.domain.repository.SpacePermissionRepository;
import com.knowledgebase.domain.repository.SpaceRepository;
import com.knowledgebase.domain.repository.UserRepository;
import com.knowledgebase.domain.repository.DocumentContentRepository;
import com.knowledgebase.application.service.SpaceService;
import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.SpacePermissionRepository;
import com.knowledgebase.interfaces.rest.advice.ErrorResponse;
import com.knowledgebase.interfaces.rest.dto.request.CreateSpaceRequest;
import com.knowledgebase.interfaces.rest.dto.request.GrantPermissionRequest;
import com.knowledgebase.interfaces.rest.dto.request.UpdateSpaceRequest;
import com.knowledgebase.interfaces.rest.dto.response.SpacePermissionResponse;
import com.knowledgebase.interfaces.rest.dto.response.SpaceResponse;
import com.knowledgebase.interfaces.rest.mapper.RestDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;


/**
 * Контроллер управления пространствами.
 *
 * Административные эндпоинты (ADMIN only):
 * - GET  /api/admin/spaces                        — список всех пространств
 * - POST /api/admin/spaces                        — создание пространства
 * - POST /api/admin/spaces/{spaceId}/permissions  — назначение прав
 *
 * Пользовательские эндпоинты (все авторизованные):
 * - GET /api/spaces — пространства, доступные текущему пользователю
 */
@RestController
@Tag(name = "Spaces", description = "Управление пространствами документов")
public class SpaceController {

    private final SpaceService spaceService;
    private final RestDtoMapper mapper;
    private final SpacePermissionRepository permissionRepository;

    public SpaceController(SpaceService spaceService, RestDtoMapper mapper, SpacePermissionRepository permissionRepository) {
        this.spaceService = spaceService;
        this.mapper = mapper;
        this.permissionRepository = permissionRepository;
    }

    // ── Административные эндпоинты ─────────────────────────────────────────

    /**
     * GET /api/admin/spaces
     * Список всех пространств (только ADMIN).
     */
    @GetMapping("/api/admin/spaces")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[ADMIN] Все пространства", description = "Возвращает список всех пространств системы")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Список пространств"),
        @ApiResponse(responseCode = "403", description = "Доступ запрещён")
    })
    public ResponseEntity<java.util.Map<String, Object>> getAllSpaces(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String status) {

        String filterStatus = (status == null || status.isEmpty()) ? "active" : status;
        List<Space> spaces = spaceService.getSpacesByStatus(filterStatus, page, size);
        long totalCount = spaceService.countSpacesByStatus(filterStatus);
        int totalPages = (int) Math.ceil((double) totalCount / size);
        List<SpaceResponse> content = spaces.stream()
                .map(mapper::toSpaceResponse)
                .toList();
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("content", content);
        response.put("totalPages", totalPages);
        response.put("totalElements", totalCount);
        response.put("number", page);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/admin/spaces
     * Создание пространства (только ADMIN).
     *
     * Если ownerId не указан — владельцем назначается текущий пользователь.
     */
    @PostMapping("/api/admin/spaces")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[ADMIN] Создать пространство",
               description = "Создаёт пространство. Владельцу автоматически назначается право OWNER.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Пространство создано",
            content = @Content(schema = @Schema(implementation = SpaceResponse.class))),
        @ApiResponse(responseCode = "409", description = "Имя пространства уже занято",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SpaceResponse> createSpace(
            @Valid @RequestBody CreateSpaceRequest request,
            @AuthenticationPrincipal User currentUser) {

        // Если ownerId не указан — используем текущего пользователя
        Long ownerId = request.ownerId() != null ? request.ownerId() : currentUser.getId();

        Space space = spaceService.createSpace(request.name(), request.description(), ownerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toSpaceResponse(space));
    }

    /**
     * GET /api/admin/spaces/{spaceId}
     * Получение данных пространства (только ADMIN).
     */
    @GetMapping("/api/admin/spaces/{spaceId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[ADMIN] Данные пространства", description = "Возвращает детальную информацию о пространстве")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Данные пространства",
            content = @Content(schema = @Schema(implementation = SpaceResponse.class))),
        @ApiResponse(responseCode = "404", description = "Пространство не найдено",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SpaceResponse> getSpace(@PathVariable Long spaceId) {
        Space space = spaceService.getSpaceById(spaceId);
        return ResponseEntity.ok(mapper.toSpaceResponse(space));
    }

    /**
     * PUT /api/admin/spaces/{spaceId}
     * Обновление данных пространства (только ADMIN).
     */
    @PutMapping("/api/admin/spaces/{spaceId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[ADMIN] Обновить пространство", description = "Обновляет данные пространства и права владельца")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Пространство обновлено",
            content = @Content(schema = @Schema(implementation = SpaceResponse.class))),
        @ApiResponse(responseCode = "404", description = "Пространство не найдено",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Имя пространства уже занято",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SpaceResponse> updateSpace(
            @PathVariable Long spaceId,
            @Valid @RequestBody UpdateSpaceRequest request) {

        Space updated = spaceService.updateSpace(
                spaceId, request.getName(), request.getDescription(), request.getOwnerId());
        return ResponseEntity.ok(mapper.toSpaceResponse(updated));
    }

    /**
     * DELETE /api/admin/spaces/{spaceId}
     * Удаление пространства (только ADMIN).
     */
    @DeleteMapping("/api/admin/spaces/{spaceId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[ADMIN] Удалить пространство", description = "Удаляет пространство и связанные права доступа")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Пространство удалено"),
        @ApiResponse(responseCode = "404", description = "Пространство не найдено",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteSpace(@PathVariable Long spaceId) {
        spaceService.deleteSpace(spaceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/admin/spaces/{spaceId}/restore
     * Восстановление пространства (только ADMIN).
     */
    @PostMapping("/api/admin/spaces/{spaceId}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[ADMIN] Восстановить пространство", description = "Восстанавливает удаленное пространство")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Пространство восстановлено"),
        @ApiResponse(responseCode = "404", description = "Пространство не найдено")
    })
    public ResponseEntity<Void> restoreSpace(@PathVariable Long spaceId) {
        spaceService.restoreSpace(spaceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/admin/spaces/{spaceId}/permissions
     * Назначение права доступа пользователю на пространство (только ADMIN).
     */
    @PostMapping("/api/admin/spaces/{spaceId}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[ADMIN] Назначить право на пространство",
               description = "Назначает право READ, WRITE или OWNER пользователю на пространство")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Право назначено",
            content = @Content(schema = @Schema(implementation = SpacePermissionResponse.class))),
        @ApiResponse(responseCode = "404", description = "Пространство или пользователь не найдены",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Такое право уже существует",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SpacePermissionResponse> grantPermission(
            @PathVariable Long spaceId,
            @Valid @RequestBody GrantPermissionRequest request) {

        SpacePermission permission = spaceService.grantPermission(
                spaceId, request.userId(), request.permissionType());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toSpacePermissionResponse(permission));
    }

    /**
     * GET /api/admin/spaces/{spaceId}/permissions
     * Получение всех прав доступа для пространства (только ADMIN).
     */
    @GetMapping("/api/admin/spaces/{spaceId}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[ADMIN] Права доступа пространства",
               description = "Возвращает все права доступа для указанного пространства")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Список прав доступа"),
        @ApiResponse(responseCode = "404", description = "Пространство не найдено"),
        @ApiResponse(responseCode = "403", description = "Доступ запрещён")
    })
    public ResponseEntity<List<SpacePermissionResponse>> getSpacePermissions(
            @PathVariable Long spaceId) {

        List<SpacePermission> permissions = spaceService.getPermissionsForSpace(spaceId);
        List<SpacePermissionResponse> response = permissions.stream()
                .map(mapper::toSpacePermissionResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/admin/permissions/{permId}
     * Отзыв права доступа (только ADMIN).
     */
    @DeleteMapping("/api/admin/permissions/{permId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[ADMIN] Отозвать право доступа", description = "Удаляет право доступа по его ID")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Право отозвано"),
        @ApiResponse(responseCode = "404", description = "Право не найдено")
    })
    public ResponseEntity<Void> revokePermission(@PathVariable Long permId) {
        permissionRepository.deleteById(permId);
        return ResponseEntity.noContent().build();
    }

    // ── Пользовательские эндпоинты ─────────────────────────────────────────

    /**
     * GET /api/spaces
     * Список пространств, доступных текущему пользователю.
     *
     * - ADMIN видит все пространства
     * - EDITOR/READER видят только пространства, где есть запись в space_permissions
     */
    @GetMapping("/api/spaces")
    @Operation(summary = "Мои пространства",
               description = "Возвращает пространства, доступные текущему пользователю. " +
                             "ADMIN видит все, остальные — только те, где есть права.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Список доступных пространств"),
        @ApiResponse(responseCode = "401", description = "Не аутентифицирован")
    })
    public ResponseEntity<List<SpaceResponse>> getMySpaces(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        boolean isAdmin = currentUser.isAdmin();
        List<Space> spaces = spaceService.getSpacesForUser(
                currentUser.getId(), isAdmin, page, size);

        List<SpaceResponse> response = spaces.stream()
                .map(mapper::toSpaceResponse)
                .toList();
        return ResponseEntity.ok(response);
    }
}
