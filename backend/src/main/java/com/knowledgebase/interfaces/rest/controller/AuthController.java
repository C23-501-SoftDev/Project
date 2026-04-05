package com.knowledgebase.interfaces.rest.controller;

import com.knowledgebase.application.service.AuthService;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.infrastructure.security.jwt.JwtTokenProvider;
import com.knowledgebase.interfaces.rest.advice.ErrorResponse;
import com.knowledgebase.interfaces.rest.dto.request.LoginRequest;
import com.knowledgebase.interfaces.rest.dto.response.LoginResponse;
import com.knowledgebase.interfaces.rest.dto.response.UserResponse;
import com.knowledgebase.interfaces.rest.mapper.RestDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер аутентификации.
 *
 * Эндпоинты:
 * - POST /api/auth/login — аутентификация, получение JWT
 * - GET  /api/auth/me    — информация о текущем пользователе
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Аутентификация и получение информации о текущем пользователе")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider tokenProvider;
    private final RestDtoMapper mapper;

    public AuthController(AuthService authService,
                          JwtTokenProvider tokenProvider,
                          RestDtoMapper mapper) {
        this.authService = authService;
        this.tokenProvider = tokenProvider;
        this.mapper = mapper;
    }

    /**
     * POST /api/auth/login
     * Аутентификация по логину и паролю.
     *
     * @param request DTO с логином и паролем
     * @return JWT токен и данные пользователя
     */
    @PostMapping("/login")
    @SecurityRequirements  // Этот эндпоинт не требует авторизации
    @Operation(
        summary = "Аутентификация",
        description = "Получение JWT-токена по логину и паролю. " +
                      "Токен передавать в заголовке: Authorization: Bearer <token>"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Успешная аутентификация",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "400", description = "Ошибка валидации",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Неверные учётные данные",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // Аутентифицируем пользователя (проверяем логин/пароль)
        User user = authService.authenticate(request.login(), request.password());

        // Генерируем JWT токен
        String token = tokenProvider.generateToken(user);

        // Формируем ответ
        LoginResponse response = new LoginResponse(token, mapper.toUserResponse(user));
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/auth/me
     * Получение информации о текущем аутентифицированном пользователе.
     *
     * @param currentUser текущий пользователь из SecurityContext (инъектируется Spring Security)
     * @return данные пользователя
     */
    @GetMapping("/me")
    @Operation(
        summary = "Текущий пользователь",
        description = "Возвращает информацию о пользователе, чей JWT токен передан в запросе"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Данные пользователя",
            content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "401", description = "Не аутентифицирован",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal User currentUser) {
        // @AuthenticationPrincipal автоматически извлекает User из SecurityContext
        // (установленного в JwtAuthenticationFilter)
        return ResponseEntity.ok(mapper.toUserResponse(currentUser));
    }
}
