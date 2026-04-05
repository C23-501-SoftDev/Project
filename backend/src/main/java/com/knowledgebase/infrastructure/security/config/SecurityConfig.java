package com.knowledgebase.infrastructure.security.config;

import com.knowledgebase.domain.repository.UserRepository;
import com.knowledgebase.infrastructure.security.jwt.JwtAccessDeniedHandler;
import com.knowledgebase.infrastructure.security.jwt.JwtAuthenticationEntryPoint;
import com.knowledgebase.infrastructure.security.jwt.JwtAuthenticationFilter;
import com.knowledgebase.infrastructure.security.jwt.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Конфигурация Spring Security (Infrastructure Layer).
 *
 * Настройки:
 * - Stateless сессии (JWT, без HTTP-сессий)
 * - CSRF отключён (REST API не использует куки для аутентификации)
 * - CORS настроен для React-фронтенда (localhost:3000)
 * - Публичные эндпоинты: /api/auth/**, /swagger-ui/**, /api-docs/**
 * - Административные эндпоинты: /api/admin/** — только ADMIN
 * - Остальные эндпоинты — требуют аутентификации
 *
 * @EnableMethodSecurity — включает @PreAuthorize на методах контроллеров
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // Включает @PreAuthorize, @PostAuthorize
public class SecurityConfig {

    /**
     * Инъекция списка CORS origins через SpEL — корректно обрабатывает YAML-списки.
     * Значение из application.yml: app.cors.allowed-origins[0], [1] и т.д.
     */
    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String[] allowedOriginsArray;

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
     * Основная цепочка фильтров Spring Security.
     *
     * Порядок фильтров:
     * JwtAuthenticationFilter → UsernamePasswordAuthenticationFilter → ...
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── CSRF: отключаем (REST API использует JWT, не куки) ──────────────
            .csrf(AbstractHttpConfigurer::disable)

            // ── CORS: настраиваем для React-фронтенда ───────────────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── Сессии: Stateless (JWT хранится на клиенте) ────────────────────
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Обработчики ошибок ──────────────────────────────────────────────
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(authenticationEntryPoint)  // 401
                    .accessDeniedHandler(accessDeniedHandler))            // 403

            // ── Правила авторизации ─────────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                    // Публичные эндпоинты — не требуют аутентификации
                    .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/api-docs/**", "/api-docs").permitAll()
                    .requestMatchers("/actuator/health").permitAll()

                    // Административные эндпоинты — только ADMIN
                    // Дополнительная проверка в контроллерах через @PreAuthorize
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")

                    // Все остальные запросы — требуют аутентификации
                    .anyRequest().authenticated()
            )

            // ── JWT-фильтр перед стандартным фильтром аутентификации ────────────
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * JWT-фильтр — бин создаётся здесь, а не через @Component,
     * чтобы избежать двойной регистрации в фильтрах сервлета.
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(tokenProvider, userRepository);
    }

    /**
     * BCrypt — стандарт для хеширования паролей.
     * Strength=10 — рекомендуемое значение для баланса безопасности и производительности.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * CORS конфигурация для React-фронтенда.
     *
     * Разрешённые источники берутся из application.yml (app.cors.allowed-origins).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Разрешённые источники (localhost:3000 для dev, реальный URL для prod)
        configuration.setAllowedOrigins(List.of(allowedOriginsArray));

        // Разрешённые HTTP-методы
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        // Разрешённые заголовки
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin"
        ));

        // Заголовки, видимые фронтенду в ответе
        configuration.setExposedHeaders(List.of("Authorization"));

        // Разрешить куки/credentials (нужно если используем Authorization заголовок)
        configuration.setAllowCredentials(true);

        // Время кеширования preflight-запроса (секунды)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
