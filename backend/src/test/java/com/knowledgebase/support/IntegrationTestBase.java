package com.knowledgebase.support;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.UserRepository;
import com.knowledgebase.domain.repository.SpaceRepository;
import com.knowledgebase.domain.repository.DocumentRepository;
import com.knowledgebase.domain.repository.AttachmentRepository;
import com.knowledgebase.application.service.DocumentService;
import com.knowledgebase.infrastructure.persistence.repository.AuditLogJpaRepository;
import com.knowledgebase.infrastructure.persistence.repository.DocumentJpaRepository;
import com.knowledgebase.infrastructure.persistence.repository.SpaceGroupPermissionJpaRepository;
import com.knowledgebase.infrastructure.persistence.repository.SpaceJpaRepository;
import com.knowledgebase.infrastructure.persistence.repository.SpacePermissionJpaRepository;
import com.knowledgebase.infrastructure.persistence.repository.AttachmentJpaRepository;
import com.knowledgebase.infrastructure.persistence.repository.UserGroupJpaRepository;
import com.knowledgebase.infrastructure.persistence.repository.UserGroupMemberJpaRepository;
import com.knowledgebase.infrastructure.persistence.repository.UserJpaRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final Path tempDir = createTempRoot();

    private static Path createTempRoot() {
        try {
            return Files.createTempDirectory("kb-it-");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory for integration tests", e);
        }
    }

    @DynamicPropertySource
    static void registerTestProperties(DynamicPropertyRegistry registry) {
        // В тестах не трогаем рабочие ./data — всё уходит во временную директорию.
        registry.add("app.storage.git.path", () -> tempDir.resolve("git-repo").toString());
        registry.add("app.storage.blob.path", () -> tempDir.resolve("blob-storage").toString());

        // Тесты должны быть герметичными: в контейнере заданы переменные окружения
        // NOTIFICATIONS_* / AI_*, а env в Spring Boot приоритетнее application.yml.
        // Фиксируем значения здесь (DynamicPropertySource приоритетнее env),
        // чтобы результат не зависел от настроек рабочего окружения.
        registry.add("app.notifications.enabled", () -> "false");
        registry.add("app.notifications.from", () -> "no-reply@knowledgebase.local");
        registry.add("app.notifications.admin-email", () -> "admin@knowledgebase.local");
        registry.add("app.ai.enabled", () -> "false");
        registry.add("app.ai.api-key", () -> "");
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected UserRepository userRepository;
    @Autowired protected SpaceRepository spaceRepository;
    @Autowired protected DocumentService documentService;
    @Autowired protected AttachmentRepository attachmentRepository;

    @Autowired protected UserJpaRepository userJpaRepository;
    @Autowired protected SpaceJpaRepository spaceJpaRepository;
    @Autowired protected SpacePermissionJpaRepository spacePermissionJpaRepository;
    @Autowired protected AttachmentJpaRepository attachmentJpaRepository;
    @Autowired protected DocumentJpaRepository documentJpaRepository;
    @Autowired protected UserGroupJpaRepository userGroupJpaRepository;
    @Autowired protected UserGroupMemberJpaRepository userGroupMemberJpaRepository;
    @Autowired protected SpaceGroupPermissionJpaRepository spaceGroupPermissionJpaRepository;
    @Autowired protected AuditLogJpaRepository auditLogJpaRepository;
    @Autowired protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        // Порядок важен из-за FK.
        auditLogJpaRepository.deleteAll();
        spaceGroupPermissionJpaRepository.deleteAll();
        userGroupMemberJpaRepository.deleteAll();
        userGroupJpaRepository.deleteAll();
        attachmentJpaRepository.deleteAll();
        documentJpaRepository.deleteAll();
        spacePermissionJpaRepository.deleteAll();
        spaceJpaRepository.deleteAll();
        userJpaRepository.deleteAll();

        // Таблица versions в MVP ещё не управляется JPA-entity, но используется в проверках удаления.
        jdbcTemplate.execute("DROP TABLE IF EXISTS versions");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS requirement_number_counters (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    space_id BIGINT NOT NULL,
                    template_id BIGINT NOT NULL,
                    next_number INT NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    CONSTRAINT uq_requirement_number_counters_space_template UNIQUE (space_id, template_id)
                )
                """);
        jdbcTemplate.execute("DELETE FROM requirement_number_counters");
    }

    protected User persistUser(String login, String rawPassword, String email, GlobalRole role) {
        String hash = passwordEncoder.encode(rawPassword);
        return userRepository.save(User.create(login, hash, email, role, false));
    }

    protected User persistUser(String login, String rawPassword, String email, GlobalRole role, boolean isAdmin) {
        String hash = passwordEncoder.encode(rawPassword);
        return userRepository.save(User.create(login, hash, email, role, isAdmin));
    }

    protected String loginAndGetJwt(String login, String password) throws Exception {
        String body = """
                {"login": "%s", "password": "%s"}
                """.formatted(login, password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        if (setCookie == null) {
            throw new AssertionError("Expected Set-Cookie header with JWT cookie");
        }
        return extractJwtFromSetCookie(setCookie);
    }

    protected Cookie jwtCookie(String jwt) {
        return new Cookie("JWT", jwt);
    }

    protected String uniqueLogin(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String extractJwtFromSetCookie(String setCookieHeader) {
        // AuthController выставляет cookie вручную в виде:
        // JWT=<token>; Path=/; HttpOnly; Max-Age=...; SameSite=Lax
        int nameStart = setCookieHeader.indexOf("JWT=");
        if (nameStart < 0) {
            throw new AssertionError("Expected JWT cookie in Set-Cookie, got: " + setCookieHeader);
        }
        int valueStart = nameStart + "JWT=".length();
        int valueEnd = setCookieHeader.indexOf(';', valueStart);
        if (valueEnd < 0) {
            valueEnd = setCookieHeader.length();
        }
        return setCookieHeader.substring(valueStart, valueEnd);
    }
}

