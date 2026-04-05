package com.knowledgebase.infrastructure.security.jwt;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Провайдер JWT-токенов (Infrastructure Layer).
 *
 * Отвечает за:
 * - Генерацию JWT при успешной аутентификации
 * - Валидацию JWT при каждом запросе
 * - Извлечение claims (userId, role) из токена
 *
 * Алгоритм подписи: HMAC-SHA256 (HS256)
 * Claims в токене:
 *   - sub: ID пользователя (String)
 *   - userId: Long ID пользователя
 *   - role: строковое значение роли (Admin, Editor, Reader)
 *   - iat: время выпуска
 *   - exp: время истечения
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    /** Claim для роли пользователя */
    public static final String CLAIM_ROLE = "role";

    /** Claim для ID пользователя */
    public static final String CLAIM_USER_ID = "userId";

    @Value("${app.jwt.secret-key}")
    private String secretKeyString;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    /**
     * Генерирует JWT-токен для аутентифицированного пользователя.
     *
     * @param user аутентифицированный пользователь
     * @return подписанный JWT токен
     */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setSubject(user.getId().toString())
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ROLE, user.getRole().getDbValue())  // "Admin", "Editor", "Reader"
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Извлекает ID пользователя из JWT токена.
     *
     * @param token JWT токен
     * @return ID пользователя
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get(CLAIM_USER_ID, Long.class);
    }

    /**
     * Извлекает роль пользователя из JWT токена.
     *
     * @param token JWT токен
     * @return глобальная роль пользователя
     */
    public GlobalRole getRoleFromToken(String token) {
        Claims claims = parseClaims(token);
        String roleStr = claims.get(CLAIM_ROLE, String.class);
        return GlobalRole.fromDbValue(roleStr);
    }

    /**
     * Проверяет валидность JWT токена.
     *
     * @param token JWT токен
     * @return true если токен валиден
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT токен истёк: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Неподдерживаемый JWT токен: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Некорректный JWT токен: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("Неверная подпись JWT токена: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Пустой или null JWT токен: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Парсит и возвращает claims из токена.
     * Выбрасывает исключение если токен невалиден или истёк.
     */
    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Создаёт ключ подписи из строки конфигурации.
     * Используется HMAC-SHA256.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKeyString.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
