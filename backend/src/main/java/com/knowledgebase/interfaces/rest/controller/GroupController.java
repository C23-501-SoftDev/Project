package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.GroupService;
import com.knowledgebase.domain.model.GroupMember;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.model.UserGroup;
import com.knowledgebase.domain.repository.UserRepository;
import com.knowledgebase.interfaces.rest.advice.ErrorResponse;
import com.knowledgebase.interfaces.rest.dto.request.AddGroupMemberRequest;
import com.knowledgebase.interfaces.rest.dto.request.CreateGroupRequest;
import com.knowledgebase.interfaces.rest.dto.request.UpdateGroupRequest;
import com.knowledgebase.interfaces.rest.dto.response.GroupMemberResponse;
import com.knowledgebase.interfaces.rest.dto.response.GroupResponse;
import com.knowledgebase.interfaces.rest.dto.response.PageResponse;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Контроллер управления группами пользователей (US4.1.8 / US4.1.9).
 *
 * Все эндпоинты доступны только администраторам:
 * - GET    /api/admin/groups                      — список групп
 * - POST   /api/admin/groups                      — создание группы
 * - GET    /api/admin/groups/{id}                 — данные группы
 * - PUT    /api/admin/groups/{id}                 — обновление группы
 * - DELETE /api/admin/groups/{id}                 — удаление группы (с отзывом прав)
 * - GET    /api/admin/groups/{id}/members         — участники группы
 * - POST   /api/admin/groups/{id}/members         — добавить пользователя в группу
 * - DELETE /api/admin/groups/{id}/members/{userId} — удалить пользователя из группы
 */
@RestController
@RequestMapping("/api/admin/groups")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Groups", description = "Управление группами пользователей (только ADMIN)")
public class GroupController {

    private final GroupService groupService;
    private final UserRepository userRepository;

    public GroupController(GroupService groupService, UserRepository userRepository) {
        this.groupService = groupService;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/admin/groups
     * Список групп с пагинацией (только ADMIN).
     */
    @GetMapping
    @Operation(summary = "[ADMIN] Список групп", description = "Возвращает все группы пользователей")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Страница списка групп"),
        @ApiResponse(responseCode = "403", description = "Доступ запрещён")
    })
    public ResponseEntity<PageResponse<GroupResponse>> getGroups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        List<GroupResponse> content = groupService.getGroups(page, size)
                .stream()
                .map(this::toResponse)
                .toList();
        long total = groupService.countGroups();
        return ResponseEntity.ok(PageResponse.of(content, page, size, total));
    }

    /**
     * POST /api/admin/groups
     * Создание группы (только ADMIN).
     */
    @PostMapping
    @Operation(summary = "[ADMIN] Создать группу", description = "Создаёт группу с уникальным названием")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Группа создана",
            content = @Content(schema = @Schema(implementation = GroupResponse.class))),
        @ApiResponse(responseCode = "409", description = "Название группы уже занято",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        UserGroup group = groupService.createGroup(request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(group));
    }

    /**
     * GET /api/admin/groups/{groupId}
     * Данные группы (только ADMIN).
     */
    @GetMapping("/{groupId}")
    @Operation(summary = "[ADMIN] Данные группы")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Данные группы",
            content = @Content(schema = @Schema(implementation = GroupResponse.class))),
        @ApiResponse(responseCode = "404", description = "Группа не найдена",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GroupResponse> getGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(toResponse(groupService.getGroupById(groupId)));
    }

    /**
     * PUT /api/admin/groups/{groupId}
     * Обновление группы (только ADMIN).
     */
    @PutMapping("/{groupId}")
    @Operation(summary = "[ADMIN] Обновить группу", description = "Обновляет название и описание группы")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Группа обновлена",
            content = @Content(schema = @Schema(implementation = GroupResponse.class))),
        @ApiResponse(responseCode = "404", description = "Группа не найдена"),
        @ApiResponse(responseCode = "409", description = "Название занято другой группой")
    })
    public ResponseEntity<GroupResponse> updateGroup(
            @PathVariable Long groupId,
            @Valid @RequestBody UpdateGroupRequest request) {
        UserGroup group = groupService.updateGroup(groupId, request.name(), request.description());
        return ResponseEntity.ok(toResponse(group));
    }

    /**
     * DELETE /api/admin/groups/{groupId}
     * Удаление группы вместе с членствами и правами на пространства (только ADMIN).
     */
    @DeleteMapping("/{groupId}")
    @Operation(summary = "[ADMIN] Удалить группу",
               description = "Удаляет группу. Все членства и права группы на пространства отзываются.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Группа удалена"),
        @ApiResponse(responseCode = "404", description = "Группа не найдена")
    })
    public ResponseEntity<Void> deleteGroup(@PathVariable Long groupId) {
        groupService.deleteGroup(groupId);
        return ResponseEntity.noContent().build();
    }

    // ── Членство (US4.1.9) ──────────────────────────────────────────────────

    /**
     * GET /api/admin/groups/{groupId}/members
     * Участники группы (только ADMIN).
     */
    @GetMapping("/{groupId}/members")
    @Operation(summary = "[ADMIN] Участники группы")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Список участников"),
        @ApiResponse(responseCode = "404", description = "Группа не найдена")
    })
    public ResponseEntity<List<GroupMemberResponse>> getMembers(@PathVariable Long groupId) {
        List<GroupMemberResponse> response = groupService.getMembers(groupId)
                .stream()
                .map(this::toMemberResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/admin/groups/{groupId}/members
     * Добавление пользователя в группу (только ADMIN).
     */
    @PostMapping("/{groupId}/members")
    @Operation(summary = "[ADMIN] Добавить пользователя в группу",
               description = "Пользователь наследует права группы на пространства")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Пользователь добавлен в группу"),
        @ApiResponse(responseCode = "404", description = "Группа или пользователь не найдены"),
        @ApiResponse(responseCode = "409", description = "Пользователь уже состоит в группе")
    })
    public ResponseEntity<GroupMemberResponse> addMember(
            @PathVariable Long groupId,
            @Valid @RequestBody AddGroupMemberRequest request) {
        GroupMember member = groupService.addMember(groupId, request.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toMemberResponse(member));
    }

    /**
     * DELETE /api/admin/groups/{groupId}/members/{userId}
     * Удаление пользователя из группы (только ADMIN).
     */
    @DeleteMapping("/{groupId}/members/{userId}")
    @Operation(summary = "[ADMIN] Удалить пользователя из группы",
               description = "Пользователь теряет права, полученные через группу")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Пользователь удалён из группы"),
        @ApiResponse(responseCode = "404", description = "Группа не найдена"),
        @ApiResponse(responseCode = "409", description = "Пользователь не состоит в группе")
    })
    public ResponseEntity<Void> removeMember(@PathVariable Long groupId, @PathVariable Long userId) {
        groupService.removeMember(groupId, userId);
        return ResponseEntity.noContent().build();
    }

    // ── Мапперы ─────────────────────────────────────────────────────────────

    private GroupResponse toResponse(UserGroup group) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                groupService.countMembers(group.getId()),
                group.getCreatedAt(),
                group.getUpdatedAt());
    }

    private GroupMemberResponse toMemberResponse(GroupMember member) {
        User user = userRepository.findByIdIncludingDeleted(member.getUserId()).orElse(null);
        return new GroupMemberResponse(
                member.getUserId(),
                user != null ? user.getLogin() : null,
                user != null ? user.getEmail() : null,
                member.getAddedAt());
    }
}
