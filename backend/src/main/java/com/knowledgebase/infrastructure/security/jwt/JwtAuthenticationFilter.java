package com.knowledgebase.infrastructure.security.jwt;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT-фильтр аутентификации (Infrastructure Layer).
 *
 * Выполняется один раз на каждый HTTP-запрос (OncePerRequestFilter).
 * Порядок в цепочке фильтров: JwtAuthenticationFilter → UsernamePasswordAuthenticationFilter
 *
 * Алгоритм:
 * 1. Извлечь Bearer токен из заголовка Authorization
 * 2. Валидировать токен (подпись, срок действия)
 * 3. Извлечь userId и role из claims
 * 4. Опционально загрузить пользователя из БД (для проверки существования)
 * 5. Установить Authentication в SecurityContext
 *
 * Если токен отсутствует или невалиден — запрос продолжается без аутентификации.
 * Контроль доступа к защищённым эндпоинтам — в SecurityConfig.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            // Шаг 1: Извлечь токен из заголовка
            String jwt = extractTokenFromRequest(request);

            // Шаг 2: Если токен есть и валиден — устанавливаем аутентификацию
            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {

                // Шаг 3: Извлечь userId и role из claims
                Long userId = tokenProvider.getUserIdFromToken(jwt);
                GlobalRole role = tokenProvider.getRoleFromToken(jwt);

                // Шаг 4: Проверить существование пользователя в БД
                // (необязательно для производительности, но важно для безопасности)
                User user = userRepository.findById(userId).orElse(null);

                if (user != null) {
                    // Шаг 5: Создать Authentication объект с GrantedAuthority
                    // Spring Security ожидает роли с префиксом ROLE_ для @PreAuthorize("hasRole(...)")
                    List<SimpleGrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority(role.getSpringSecurityRole())
                    );

                    // Создаём Principal — передаём User объект для доступа в контроллерах
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user, null, authorities);

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    // Устанавливаем аутентификацию в SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("Пользователь аутентифицирован: userId={}, role={}", userId, role);
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке JWT: {}", e.getMessage());
            // НЕ прерываем цепочку — Spring Security сам вернёт 401
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Извлекает JWT токен из заголовка Authorization.
     * Ожидаемый формат: "Bearer <token>"
     *
     * @param request HTTP запрос
     * @return токен без префикса "Bearer ", или null
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
