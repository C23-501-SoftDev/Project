package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.UserService;
import com.knowledgebase.domain.exception.AccessDeniedException;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.interfaces.rest.advice.ErrorResponse;
import com.knowledgebase.interfaces.rest.dto.request.ChangePasswordRequest;
import com.knowledgebase.interfaces.rest.dto.request.CreateUserRequest;
import com.knowledgebase.interfaces.rest.dto.request.UpdateUserRequest;
import com.knowledgebase.interfaces.rest.dto.response.PageResponse;
import com.knowledgebase.interfaces.rest.dto.response.UserResponse;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Контроллер панели администратора — управление пользователями.
 *
 * Все эндпоинты доступны только для ADMIN.
 * Двойная защита:
 * 1. SecurityConfig: .requestMatchers("/api/admin/**").hasRole("ADMIN")
 * 2. @PreAuthorize("hasRole('ADMIN')") на каждом методе (явная документация в коде)
 *
 * Префикс: /api/admin/users
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")  // Весь контроллер — только для ADMIN
@Tag(name = "Admin: Users", description = "Управление пользователями (только для ADMIN)")
public class AdminUserController {

    private final UserService userService;
    private final RestDtoMapper mapper;

    public AdminUserController(UserService userService, RestDtoMapper mapper) {
        this.userService = userService;
        this.mapper = mapper;
    }

    /**
     * GET /api/admin/users
     * Список всех пользователей с пагинацией и фильтрами.
     * Фильтры: status (active/deleted/all), roles (список ролей), search (по login/email)
     */
    @GetMapping
    @Operation(summary = "Список пользователей", description = "Возвращает список пользователей с пагинацией и фильтрами.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Список пользователей"),
        @ApiResponse(responseCode = "403", description = "Доступ запрещён",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<UserResponse>> getAllUsers(
            @Parameter(description = "Номер страницы (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Размер страницы", example = "20")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Поле сортировки", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sortBy,

            @Parameter(description = "Направление сортировки", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDir,

            @Parameter(description = "Фильтр по статусу: active, deleted, all", example = "active")
            @RequestParam(defaultValue = "active") String status,

            @Parameter(description = "Фильтр по ролям (можно указать несколько)", example = "READER,EDITOR")
            @RequestParam(required = false) List<String> roles,

            @Parameter(description = "Поиск по логину или email", example = "admin")
            @RequestParam(required = false, defaultValue = "") String search) {

        List<User> users = userService.getUsersWithFilters(page, size, sortBy, sortDir, status, roles, search);
        long total = userService.countUsersWithFilters(status, roles, search);

        List<UserResponse> userResponses = users.stream()
                .map(mapper::toUserResponse)
                .toList();

        return ResponseEntity.ok(PageResponse.of(userResponses, page, size, total));
    }

    /**
     * GET /api/admin/users/{id}
     * Получить пользователя по ID (включая удалённых).
     */
    @GetMapping("/{id}")
    @Operation(summary = "Получить пользователя", description = "Возвращает данные пользователя по ID, включая удалённых")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Данные пользователя",
            content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "404", description = "Пользователь не найден",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "ID пользователя", required = true)
            @PathVariable Long id) {
        User user = userService.getUserByIdIncludingDeleted(id);
        return ResponseEntity.ok(mapper.toUserResponse(user));
    }

    /**
     * POST /api/admin/users
     * Создать нового пользователя.
     */
    @PostMapping
    @Operation(summary = "Создать пользователя",
               description = "Создаёт нового пользователя. Пароль хешируется BCrypt.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Пользователь создан",
            content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "400", description = "Ошибка валидации",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Логин или email уже существуют",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(
                request.login(),
                request.email(),
                request.password(),
                request.role(),
                request.isAdmin());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toUserResponse(user));
    }

    /**
     * PUT /api/admin/users/{id}
     * Обновить данные пользователя (логин, email, роль, isAdmin).
     * Пароль не обновляется — используйте PUT /api/admin/users/{id}/password.
     *
     * ВАЖНО: При изменении роли новые права вступят в силу только после
     * повторной аутентификации пользователя (т.к. роль закодирована в JWT).
     */
    @PutMapping("/{id}")
    @Operation(summary = "Обновить пользователя",
               description = "Обновляет логин, email, роль и/или isAdmin. " +
                             "Изменение роли вступит в силу после следующего входа пользователя.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Пользователь обновлён",
            content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Пользователь не найден",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Конфликт данных",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal User currentUser) {
        // isAdmin может быть null в request, используем текущее значение
        User targetUser = userService.getUserById(id);
        boolean isAdmin = request.isAdmin() != null ? request.isAdmin() : targetUser.getIsAdmin();

        // Запрещаем администратору снимать с себя права админа
        if (currentUser.getId().equals(id) && targetUser.getIsAdmin() && !isAdmin) {
            throw new AccessDeniedException("Администратор не может снять с себя права администратора");
        }

        User user = userService.updateUser(id, request.login(), request.email(), request.role(), isAdmin, currentUser.getId());
        return ResponseEntity.ok(mapper.toUserResponse(user));
    }

    /**
     * DELETE /api/admin/users/{id}
     * Выполняет soft-delete пользователя.
     * Данные пользователя сохраняются для истории авторства.
     * Возвращает 200 с данными пользователя (а не 204).
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить пользователя (soft-delete)",
               description = "Выполняет soft-delete. Данные сохраняются для истории. " +
                             "Возвращает данные удалённого пользователя.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Пользователь soft-удалён",
            content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "404", description = "Пользователь не найден",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> deleteUser(@PathVariable Long id) {
        User user = userService.deleteUser(id);
        return ResponseEntity.ok(mapper.toUserResponse(user));
    }

    /**
     * POST /api/admin/users/{id}/restore
     * Восстановить soft-удалённого пользователя.
     */
    @PostMapping("/{id}/restore")
    @Operation(summary = "Восстановить пользователя",
               description = "Восстанавливает soft-удалённого пользователя.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Пользователь восстановлен",
            content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "404", description = "Пользователь не найден",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Пользователь не был удалён",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> restoreUser(@PathVariable Long id) {
        User user = userService.restoreUser(id);
        return ResponseEntity.ok(mapper.toUserResponse(user));
    }

    /**
     * PUT /api/admin/users/{id}/password
     * Сброс/изменение пароля пользователя.
     */
    @PutMapping("/{id}/password")
    @Operation(summary = "Сброс пароля",
               description = "Устанавливает новый пароль для пользователя. Пароль хешируется BCrypt.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Пароль изменён"),
        @ApiResponse(responseCode = "400", description = "Ошибка валидации (пароль слишком короткий)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Пользователь не найден",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> changePassword(
            @PathVariable Long id,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(id, request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
