package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.dto.PagedResult;
import com.knowledgebase.application.service.SpaceService;
import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.interfaces.rest.advice.ErrorResponse;
import com.knowledgebase.interfaces.rest.dto.request.CreateSpaceRequest;
import com.knowledgebase.interfaces.rest.dto.request.GrantPermissionRequest;
import com.knowledgebase.interfaces.rest.dto.response.PageResponse;
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

    public SpaceController(SpaceService spaceService, RestDtoMapper mapper) {
        this.spaceService = spaceService;
        this.mapper = mapper;
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
    public ResponseEntity<PageResponse<SpaceResponse>> getAllSpaces(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PagedResult<Space> result = spaceService.getAllSpaces(page, size);
        List<SpaceResponse> content = result.items().stream()
                .map(mapper::toSpaceResponse)
                .toList();
        return ResponseEntity.ok(PageResponse.of(content, page, size, result.totalElements()));
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
    public ResponseEntity<PageResponse<SpaceResponse>> getMySpaces(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        boolean isAdmin = currentUser.isAdmin();
        PagedResult<Space> result = spaceService.getSpacesForUser(
                currentUser.getId(), isAdmin, page, size);

        List<SpaceResponse> content = result.items().stream()
                .map(mapper::toSpaceResponse)
                .toList();
        return ResponseEntity.ok(PageResponse.of(content, page, size, result.totalElements()));
    }
}
