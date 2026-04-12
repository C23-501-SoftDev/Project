package com.knowledgebase.infrastructure.security.jwt;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
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
import java.util.Arrays;
import java.util.List;

/**
 * JWT Cookie фильтр аутентификации (Infrastructure Layer).
 *
 * Извлекает JWT-токен из HttpOnly Cookie вместо заголовка Authorization.
 * Выполняется один раз на каждый HTTP-запрос (OncePerRequestFilter).
 *
 * Алгоритм:
 * 1. Извлечь JWT токен из Cookie (имя cookie: "JWT")
 * 2. Валидировать токен (подпись, срок действия)
 * 3. Извлечь userId и role из claims
 * 4. Загрузить пользователя из БД для проверки существования
 * 5. Установить Authentication в SecurityContext
 *
 * Если токен отсутствует или невалиден — запрос продолжается без аутентификации.
 */
public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtCookieAuthenticationFilter.class);
    private static final String JWT_COOKIE_NAME = "JWT";

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    public JwtCookieAuthenticationFilter(JwtTokenProvider tokenProvider, UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = extractTokenFromCookie(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                Long userId = tokenProvider.getUserIdFromToken(jwt);
                GlobalRole role = tokenProvider.getRoleFromToken(jwt);

                User user = userRepository.findById(userId).orElse(null);

                if (user != null) {
                    List<SimpleGrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority(role.getSpringSecurityRole())
                    );

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user, null, authorities);

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("Пользователь аутентифицирован через Cookie: userId={}, role={}", userId, role);
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке JWT Cookie: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Извлекает JWT токен из Cookie.
     *
     * @param request HTTP запрос
     * @return токен или null если отсутствует
     */
    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> JWT_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }
}
