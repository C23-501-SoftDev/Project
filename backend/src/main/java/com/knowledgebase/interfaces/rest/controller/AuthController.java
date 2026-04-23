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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер аутентификации для SSR + REST API.
 *
 * MVC эндпоинты (SSR):
 * - GET  /login            — страница логина
 * - POST /login            — обработка формы логина, установка JWT Cookie
 * - GET  /logout           — страница выхода
 * - POST /logout           — очистка JWT Cookie и выход
 *
 * REST API эндпоинты (для AJAX от Tiptap Editor):
 * - POST /api/auth/login   — аутентификация, получение JWT (JSON)
 * - GET  /api/auth/me      — информация о текущем пользователе (JSON)
 */
@Controller
@RequestMapping
@Tag(name = "Authentication", description = "Аутентификация и получение информации о текущем пользователе")
public class AuthController {

    private static final String JWT_COOKIE_NAME = "JWT";

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

    // =====================================================
    // MVC ENDPOINTS (SSR — Thymeleaf)
    // =====================================================

    /**
     * GET /login
     * Страница аутентификации.
     */
    @GetMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Страница входа", description = "Возвращает HTML-страницу с формой входа")
    public String loginPage(@RequestParam(required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("errorMessage", "Неверный логин или пароль");
        }
        return "login";
    }

    /**
     * POST /login
     * Обработка формы входа — установка JWT Cookie и редирект.
     */
    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Вход через форму", description = "Аутентификация и установка JWT Cookie")
    public String loginForm(@RequestParam String username,
                           @RequestParam String password,
                           HttpServletResponse response) {
        User user = authService.authenticate(username, password);
        String token = tokenProvider.generateToken(user);

        setJwtCookie(response, token);

        return "redirect:/";
    }

    /**
     * POST /logout
     * Выход из системы — очистка JWT Cookie и редирект на страницу входа.
     */
    @PostMapping("/logout")
    @Operation(summary = "Выход из системы", description = "Очистка JWT Cookie и редирект на страницу входа")
    public String logout(HttpServletResponse response) {
        clearJwtCookie(response);
        return "redirect:/login";
    }

    // =====================================================
    // REST API ENDPOINTS (для AJAX от Tiptap Editor)
    // =====================================================

    /**
     * POST /api/auth/login
     * Аутентификация по логину и паролю (REST API).
     */
    @PostMapping("/api/auth/login")
    @ResponseBody
    @SecurityRequirements
    @Operation(
        summary = "Аутентификация (REST API)",
        description = "Получение JWT-токена по логину и паролю. " +
                      "Токен передаётся в HttpOnly Cookie автоматически."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Успешная аутентификация",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "400", description = "Ошибка валидации",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Неверные учётные данные",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<LoginResponse> loginApi(@Valid @RequestBody LoginRequest request,
                                                  HttpServletResponse response) {
        User user = authService.authenticate(request.login(), request.password());
        String token = tokenProvider.generateToken(user);

        setJwtCookie(response, token);

        LoginResponse loginResponse = new LoginResponse(token, mapper.toUserResponse(user));
        return ResponseEntity.ok(loginResponse);
    }

    /**
     * GET /api/auth/me
     * Получение информации о текущем аутентифицированном пользователе.
     */
    @GetMapping("/api/auth/me")
    @ResponseBody
    @Operation(
        summary = "Текущий пользователь (REST API)",
        description = "Возвращает информацию о пользователе, чей JWT Cookie установлен"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Данные пользователя",
            content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "401", description = "Не аутентифицирован",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(mapper.toUserResponse(currentUser));
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    /**
     * Устанавливает JWT Cookie с явным SameSite=Lax.
     * jakarta.servlet.http.Cookie не поддерживает setSameSite(),
     * поэтому заголовок Set-Cookie формируется вручную.
     */
    private void setJwtCookie(HttpServletResponse response, String token) {
        response.addHeader("Set-Cookie",
                String.format("%s=%s; Path=/; HttpOnly; Max-Age=%d; SameSite=Lax",
                        JWT_COOKIE_NAME, token, 86400));
    }

    /**
     * Очищает JWT Cookie с явным SameSite=Lax.
     * jakarta.servlet.http.Cookie не поддерживает setSameSite(),
     * поэтому заголовок Set-Cookie формируется вручную.
     */
    private void clearJwtCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie",
                String.format("%s=; Path=/; HttpOnly; Max-Age=0; SameSite=Lax",
                        JWT_COOKIE_NAME));

        response.addHeader("Set-Cookie",
                String.format("XSRF-TOKEN=; Path=/; Max-Age=0; SameSite=Lax"));
    }
}
