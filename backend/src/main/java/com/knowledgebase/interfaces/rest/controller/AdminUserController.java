package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.UserService;
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
     * Список всех пользователей с пагинацией.
     */
    @GetMapping
    @Operation(summary = "Список пользователей", description = "Возвращает список всех пользователей с пагинацией")
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
            @RequestParam(defaultValue = "desc") String sortDir) {

        List<User> users = userService.getAllUsers(page, size, sortBy, sortDir);
        long total = userService.countUsers();

        List<UserResponse> userResponses = users.stream()
                .map(mapper::toUserResponse)
                .toList();

        return ResponseEntity.ok(PageResponse.of(userResponses, page, size, total));
    }

    /**
     * GET /api/admin/users/{id}
     * Получить пользователя по ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Получить пользователя", description = "Возвращает данные пользователя по ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Данные пользователя",
            content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "404", description = "Пользователь не найден",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "ID пользователя", required = true)
            @PathVariable Long id) {
        User user = userService.getUserById(id);
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
                request.role());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toUserResponse(user));
    }

    /**
     * PUT /api/admin/users/{id}
     * Обновить данные пользователя (логин, email, роль).
     * Пароль не обновляется — используйте PUT /api/admin/users/{id}/password.
     *
     * ВАЖНО: При изменении роли новые права вступят в силу только после
     * повторной аутентификации пользователя (т.к. роль закодирована в JWT).
     */
    @PutMapping("/{id}")
    @Operation(summary = "Обновить пользователя",
               description = "Обновляет логин, email и/или роль. " +
                             "Изменение роли вступит в силу после следующего входа пользователя.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Пользователь обновлён",
            content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "404", description = "Пользователь не найден",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Конфликт данных",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        User user = userService.updateUser(id, request.login(), request.email(), request.role());
        return ResponseEntity.ok(mapper.toUserResponse(user));
    }

    /**
     * DELETE /api/admin/users/{id}
     * Удалить пользователя.
     *
     * Проверяет наличие связанных данных (ON DELETE RESTRICT):
     * - Нельзя удалить если есть документы (author_id)
     * - Нельзя удалить если является владельцем пространств (owner_id)
     * - Нельзя удалить если создавал версии (versions.author_id)
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить пользователя",
               description = "Физически удаляет пользователя. " +
                             "Возвращает 409 Conflict если у пользователя есть документы, пространства или версии.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Пользователь удалён"),
        @ApiResponse(responseCode = "404", description = "Пользователь не найден",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Невозможно удалить (есть связанные данные)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
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
