package com.knowledgebase.infrastructure.security.config;

import com.knowledgebase.domain.repository.UserRepository;
import com.knowledgebase.infrastructure.security.jwt.JwtAccessDeniedHandler;
import com.knowledgebase.infrastructure.security.jwt.JwtAuthenticationEntryPoint;
import com.knowledgebase.infrastructure.security.jwt.JwtCookieAuthenticationFilter;
import com.knowledgebase.infrastructure.security.jwt.JwtTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Конфигурация Spring Security для SSR с Thymeleaf (Infrastructure Layer).
 *
 * Настройки:
 * - Stateless сессии (JWT в HttpOnly Cookie)
 * - CSRF защита с Cookie-based токеном (для AJAX запросов от Tiptap Editor)
 * - Публичные эндпоинты: /login, /api/auth/**, /swagger-ui/**, /api-docs/**
 * - Административные эндпоинты: /admin/**, /api/admin/** — только ADMIN
 * - Остальные эндпоинты — требуют аутентификации
 *
 * @EnableMethodSecurity — включает @PreAuthorize на методах контроллеров
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtTokenProvider tokenProvider,
                          UserRepository userRepository,
                          JwtAuthenticationEntryPoint authenticationEntryPoint,
                          JwtAccessDeniedHandler accessDeniedHandler) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /**
     * Основная цепочка фильтров Spring Security для SSR.
     *
     * Порядок фильтров:
     * JwtCookieAuthenticationFilter → CsrfFilter → UsernamePasswordAuthenticationFilter → ...
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // CSRF обработчик для чтения токена из cookie
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName("_csrf");

        http
            // ── CSRF: включаем с Cookie-based хранилищем ──────────────
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(requestHandler)
                // Исключаем API эндпоинты и SSR форму логина (CSRF на /login не критичен для безопасности)
                .ignoringRequestMatchers("/api/**", "/login", "/logout")
            )

            // ── Сессии: Stateless (JWT хранится в HttpOnly Cookie) ────────────────────
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Logout: отключаем встроенный LogoutFilter, обрабатываем в AuthController ──
            .logout(logout -> logout.disable())

            // ── Обработчики ошибок ──────────────────────────────────────────────
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(authenticationEntryPoint)  // 401
                    .accessDeniedHandler(accessDeniedHandler))            // 403

            // ── Правила авторизации ─────────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                    // Страницы логина и ресурсы — публичные
                    .requestMatchers("/login", "/error").permitAll()
                    
                    // Статические ресурсы (CSS, JS, изображения)
                    .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                    
                    // Публичные API эндпоинты
                    .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/api-docs/**", "/api-docs").permitAll()
                    .requestMatchers("/actuator/health").permitAll()

                    // Административные страницы — только ADMIN
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    
                    // Административные API эндпоинты — только ADMIN
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")

                    // Все остальные запросы — требуют аутентификации
                    .anyRequest().authenticated()
            )

            // ── JWT Cookie фильтр перед стандартным фильтром аутентификации ────────────
            .addFilterBefore(jwtCookieAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * JWT Cookie фильтр — бин создаётся здесь для инъекции зависимостей.
     */
    @Bean
    public JwtCookieAuthenticationFilter jwtCookieAuthenticationFilter() {
        return new JwtCookieAuthenticationFilter(tokenProvider, userRepository);
    }

    /**
     * BCrypt — стандарт для хеширования паролей.
     * Strength=10 — рекомендуемое значение для баланса безопасности и производительности.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
