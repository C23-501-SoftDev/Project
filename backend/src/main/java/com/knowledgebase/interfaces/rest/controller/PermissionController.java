package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.PermissionService;
import com.knowledgebase.domain.model.PermissionType;
import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.application.dto.PagedResult;
import com.knowledgebase.application.service.SpaceService;
import com.knowledgebase.interfaces.rest.advice.ErrorResponse;
import com.knowledgebase.interfaces.rest.dto.response.PageResponse;
import com.knowledgebase.interfaces.rest.dto.response.SpaceResponse;
import com.knowledgebase.interfaces.rest.dto.response.UserPermissionsResponse;
import com.knowledgebase.interfaces.rest.mapper.RestDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Контроллер прав доступа пользователей.
 *
 * Эндпоинты:
 * - GET /api/user/permissions?spaceId={id} — права текущего пользователя в пространстве
 * - GET /api/user/spaces                   — все пространства с правами пользователя
 */
@RestController
@RequestMapping("/api/user")
@Tag(name = "User Permissions", description = "Получение информации о правах текущего пользователя")
public class PermissionController {

    private final PermissionService permissionService;
    private final SpaceService spaceService;
    private final RestDtoMapper mapper;

    public PermissionController(PermissionService permissionService,
                                SpaceService spaceService,
                                RestDtoMapper mapper) {
        this.permissionService = permissionService;
        this.spaceService = spaceService;
        this.mapper = mapper;
    }

    /**
     * GET /api/user/permissions?spaceId={id}
     * Возвращает права текущего пользователя в конкретном пространстве.
     *
     * Используется фронтендом для:
     * - Отображения/скрытия кнопок редактирования
     * - Проверки перед созданием документа
     *
     * Для ADMIN возвращает [READ, WRITE, OWNER] с флагами canRead=true, canEdit=true, canCreate=true.
     */
    @GetMapping("/permissions")
    @Operation(
        summary = "Мои права в пространстве",
        description = "Возвращает список прав текущего пользователя в пространстве и флаги canRead/canEdit/canCreate. " +
                      "Используется фронтендом для скрытия кнопок управления."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Права пользователя",
            content = @Content(schema = @Schema(implementation = UserPermissionsResponse.class))),
        @ApiResponse(responseCode = "401", description = "Не аутентифицирован",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Пространство не найдено",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserPermissionsResponse> getMyPermissions(
            @Parameter(description = "ID пространства", required = true, example = "1")
            @RequestParam Long spaceId,

            @AuthenticationPrincipal User currentUser) {

        // Получаем список прав (для ADMIN — все, для остальных — из space_permissions)
        List<PermissionType> permissions = permissionService.getUserPermissions(
                currentUser.getId(), currentUser.getRole(), spaceId);

        // Получаем флаги для UI
        PermissionService.PermissionFlags flags = permissionService.getPermissionFlags(
                currentUser.getId(), currentUser.getRole(), spaceId);

        UserPermissionsResponse response = new UserPermissionsResponse(
                spaceId,
                permissions,
                flags.canRead,
                flags.canEdit,
                flags.canCreate
        );
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/user/spaces
     * Возвращает все пространства, к которым у пользователя есть доступ.
     *
     * ADMIN → все пространства
     * EDITOR/READER → только пространства с записями в space_permissions
     */
    @GetMapping("/spaces")
    @Operation(
        summary = "Мои пространства",
        description = "Возвращает все пространства, к которым у пользователя есть доступ (любое право)"
    )
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
