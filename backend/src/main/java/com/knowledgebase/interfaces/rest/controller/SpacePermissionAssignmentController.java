package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.SpacePermissionAssignmentService;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.UserRepository;
import com.knowledgebase.interfaces.rest.advice.ErrorResponse;
import com.knowledgebase.interfaces.rest.dto.request.AssignSpacePermissionRequest;
import com.knowledgebase.interfaces.rest.dto.request.ChangeSpacePermissionRequest;
import com.knowledgebase.interfaces.rest.dto.response.SpacePermissionAssignmentResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/spaces/{spaceId}/permission-assignments")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Space Permission Assignments",
        description = "Механизм назначения прав доступа к пространствам (только ADMIN)")
public class SpacePermissionAssignmentController {

    private final SpacePermissionAssignmentService assignmentService;
    private final UserRepository userRepository;

    public SpacePermissionAssignmentController(SpacePermissionAssignmentService assignmentService,
                                               UserRepository userRepository) {
        this.assignmentService = assignmentService;
        this.userRepository = userRepository;
    }

    @PostMapping
    @Operation(summary = "[ADMIN] Назначить право доступа",
            description = "Назначает пользователю право READ, WRITE или OWNER на пространство.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Право назначено",
                    content = @Content(schema = @Schema(implementation = SpacePermissionAssignmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации запроса",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Пространство или пользователь не найдены",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Право такого типа уже назначено",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Назначение нарушает бизнес-правила",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SpacePermissionAssignmentResponse> assign(
            @Parameter(description = "ID пространства", required = true) @PathVariable Long spaceId,
            @Valid @RequestBody AssignSpacePermissionRequest request) {

        SpacePermission saved = assignmentService.assign(
                spaceId, request.userId(), request.permissionType());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(saved));
    }

    @GetMapping
    @Operation(summary = "[ADMIN] Список назначений пространства",
            description = "Возвращает все назначения прав для указанного пространства.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список назначений"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Пространство не найдено",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<SpacePermissionAssignmentResponse>> listForSpace(
            @Parameter(description = "ID пространства", required = true) @PathVariable Long spaceId) {

        List<SpacePermission> assignments = assignmentService.listAssignments(spaceId);
        return ResponseEntity.ok(toResponses(assignments));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "[ADMIN] Права пользователя в пространстве",
            description = "Возвращает все назначенные права конкретного пользователя в пространстве.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список прав пользователя"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Пространство или пользователь не найдены",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<SpacePermissionAssignmentResponse>> listForUser(
            @PathVariable Long spaceId,
            @PathVariable Long userId) {

        List<SpacePermission> assignments = assignmentService.listAssignmentsForUser(spaceId, userId);
        return ResponseEntity.ok(toResponses(assignments));
    }

    @PutMapping("/{permissionId}")
    @Operation(summary = "[ADMIN] Изменить тип права",
            description = "Изменяет тип уже назначенного права. " +
                    "Защищает от понижения единственного OWNER пространства.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Тип права изменён",
                    content = @Content(schema = @Schema(implementation = SpacePermissionAssignmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации запроса",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Право не найдено",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Запись с таким типом уже существует или " +
                    "нельзя понизить единственного OWNER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Новый тип совпадает с текущим",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SpacePermissionAssignmentResponse> changeType(
            @PathVariable Long spaceId,
            @PathVariable Long permissionId,
            @Valid @RequestBody ChangeSpacePermissionRequest request) {

        SpacePermission updated = assignmentService.changeType(
                spaceId, permissionId, request.permissionType());

        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{permissionId}")
    @Operation(summary = "[ADMIN] Отозвать право",
            description = "Удаляет конкретное назначение прав. " +
                    "Защищает от удаления единственного OWNER пространства.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Право отозвано"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Право не найдено",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Невозможно отозвать единственного OWNER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> revoke(
            @PathVariable Long spaceId,
            @PathVariable Long permissionId) {

        assignmentService.revoke(spaceId, permissionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{userId}")
    @Operation(summary = "[ADMIN] Отозвать все права пользователя в пространстве",
            description = "Удаляет все назначения прав указанного пользователя в пространстве. " +
                    "Защищает от удаления единственного OWNER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Права отозваны"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Пространство, пользователь или права не найдены",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Нельзя удалить все права единственного OWNER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, Object>> revokeAllForUser(
            @PathVariable Long spaceId,
            @PathVariable Long userId) {

        assignmentService.revokeAllForUser(spaceId, userId);
        return ResponseEntity.ok(Map.of(
                "spaceId", spaceId,
                "userId", userId,
                "status", "revoked"
        ));
    }

    private SpacePermissionAssignmentResponse toResponse(SpacePermission permission) {
        Optional<User> user = userRepository.findByIdIncludingDeleted(permission.getUserId());
        return SpacePermissionAssignmentResponse.from(permission, user.orElse(null));
    }

    private List<SpacePermissionAssignmentResponse> toResponses(List<SpacePermission> permissions) {
        return permissions.stream().map(this::toResponse).toList();
    }
}
