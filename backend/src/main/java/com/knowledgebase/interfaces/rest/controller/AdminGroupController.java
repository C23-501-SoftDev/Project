package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.UserGroupService;
import com.knowledgebase.domain.model.UserGroup;
import com.knowledgebase.interfaces.rest.advice.ErrorResponse;
import com.knowledgebase.interfaces.rest.dto.request.CreateGroupRequest;
import com.knowledgebase.interfaces.rest.dto.request.UpdateGroupRequest;
import com.knowledgebase.interfaces.rest.dto.response.GroupResponse;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Контроллер панели администратора — управление группами пользователей (US4.1.8).
 *
 * Все эндпоинты доступны только для ADMIN.
 * Двойная защита:
 * 1. SecurityConfig: .requestMatchers("/api/admin/**").hasRole("ADMIN")
 * 2. @PreAuthorize("hasRole('ADMIN')") на уровне контроллера
 *
 * Префикс: /api/admin/groups
 */
@RestController
@RequestMapping("/api/admin/groups")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Groups", description = "Управление группами пользователей (только для ADMIN)")
public class AdminGroupController {

    private final UserGroupService groupService;
    private final RestDtoMapper mapper;

    public AdminGroupController(UserGroupService groupService, RestDtoMapper mapper) {
        this.groupService = groupService;
        this.mapper = mapper;
    }

    /**
     * GET /api/admin/groups
     * Список всех групп с пагинацией.
     */
    @GetMapping
    @Operation(summary = "Список групп", description = "Возвращает список групп пользователей с пагинацией")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Список групп"),
        @ApiResponse(responseCode = "403", description = "Доступ запрещён",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<GroupResponse>> getAllGroups(
            @Parameter(description = "Номер страницы (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Размер страницы", example = "50")
            @RequestParam(defaultValue = "50") int size) {

        List<GroupResponse> content = groupService.getAllGroups(page, size).stream()
                .map(mapper::toGroupResponse)
                .toList();
        long total = groupService.countGroups();
        return ResponseEntity.ok(PageResponse.of(content, page, size, total));
    }

    /**
     * GET /api/admin/groups/{groupId}
     * Получить группу по ID.
     */
    @GetMapping("/{groupId}")
    @Operation(summary = "Получить группу", description = "Возвращает данные группы по ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Данные группы",
            content = @Content(schema = @Schema(implementation = GroupResponse.class))),
        @ApiResponse(responseCode = "404", description = "Группа не найдена",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupResponse> getGroup(@PathVariable Long groupId) {
        UserGroup group = groupService.getGroupById(groupId);
        return ResponseEntity.ok(mapper.toGroupResponse(group));
    }

    /**
     * POST /api/admin/groups
     * Создать новую группу.
     */
    @PostMapping
    @Operation(summary = "Создать группу", description = "Создаёт новую группу с уникальным названием")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Группа создана",
            content = @Content(schema = @Schema(implementation = GroupResponse.class))),
        @ApiResponse(responseCode = "400", description = "Ошибка валидации",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Название группы уже занято",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        UserGroup group = groupService.createGroup(request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toGroupResponse(group));
    }

    /**
     * PUT /api/admin/groups/{groupId}
     * Обновить название и описание группы.
     */
    @PutMapping("/{groupId}")
    @Operation(summary = "Обновить группу", description = "Обновляет название и описание группы")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Группа обновлена",
            content = @Content(schema = @Schema(implementation = GroupResponse.class))),
        @ApiResponse(responseCode = "404", description = "Группа не найдена",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Название группы уже занято",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupResponse> updateGroup(
            @PathVariable Long groupId,
            @Valid @RequestBody UpdateGroupRequest request) {
        UserGroup group = groupService.updateGroup(groupId, request.name(), request.description());
        return ResponseEntity.ok(mapper.toGroupResponse(group));
    }

    /**
     * DELETE /api/admin/groups/{groupId}
     * Удалить группу. Состав группы удаляется каскадно, пользователи не затрагиваются.
     */
    @DeleteMapping("/{groupId}")
    @Operation(summary = "Удалить группу",
               description = "Удаляет группу. Связи с пользователями и права группы удаляются каскадно.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Группа удалена"),
        @ApiResponse(responseCode = "404", description = "Группа не найдена",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteGroup(@PathVariable Long groupId) {
        groupService.deleteGroup(groupId);
        return ResponseEntity.noContent().build();
    }
}
