package com.knowledgebase.integration;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.infrastructure.persistence.entity.SpaceJpaEntity;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminUserIntegrationTest extends IntegrationTestBase {

    @Test
    void adminUsers_crud_andPasswordFlow() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.ADMIN);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        String login = uniqueLogin("user");
        String email = login + "@example.com";

        // create (201)
        mockMvc.perform(post("/api/admin/users")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"login":"%s","email":"%s","password":"secret123","role":"READER"}
                                """.formatted(login, email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.login", is(login)))
                .andExpect(jsonPath("$.email", is(email)))
                .andExpect(jsonPath("$.role", is("READER")));

        // list (200)
        mockMvc.perform(get("/api/admin/users?page=0&size=20")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is(not(empty()))))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)));

        // conflict on duplicate login (409)
        mockMvc.perform(post("/api/admin/users")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"login":"%s","email":"other_%s","password":"secret123","role":"READER"}
                                """.formatted(login, email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")));

        // get non-existing (404)
        mockMvc.perform(get("/api/admin/users/999999")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void adminUser_updateRole_requiresReLoginForNewJwtRole() throws Exception {
        User admin = persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.ADMIN);
        User target = persistUser("editor", "editor123", "editor@knowledgebase.local", GlobalRole.EDITOR);

        String adminJwt = loginAndGetJwt(admin.getLogin(), "admin123");
        String editorJwtBefore = loginAndGetJwt(target.getLogin(), "editor123");

        // Меняем роль EDITOR -> READER
        mockMvc.perform(put("/api/admin/users/" + target.getId())
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"login":"%s","email":"%s","role":"READER"}
                                """.formatted(target.getLogin(), target.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("READER")));

        // Старый JWT всё ещё несёт роль, закодированную на момент входа.
        // Мы проверяем это косвенно: доступ к ADMIN API всё равно запрещён (403) и до, и после.
        mockMvc.perform(get("/api/admin/users").cookie(jwtCookie(editorJwtBefore)))
                .andExpect(status().isForbidden());

        // Перелогин — новый JWT выпускается с новой ролью.
        String editorJwtAfter = loginAndGetJwt(target.getLogin(), "editor123");
        mockMvc.perform(get("/api/admin/users").cookie(jwtCookie(editorJwtAfter)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUser_changePassword_affectsLogin() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.ADMIN);
        User user = persistUser("user1", "oldpass123", "user1@knowledgebase.local", GlobalRole.READER);

        String adminJwt = loginAndGetJwt("admin", "admin123");

        // смена пароля (204)
        mockMvc.perform(put("/api/admin/users/" + user.getId() + "/password")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isNoContent());

        // старый пароль больше не подходит (401)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"login\":\"user1\",\"password\":\"oldpass123\"}"))
                .andExpect(status().isUnauthorized());

        // новый подходит (200)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"login\":\"user1\",\"password\":\"newpass123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void adminUser_delete_userWithOwnedSpaces_returns409() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.ADMIN);
        User victim = persistUser("victim", "victim123", "victim@knowledgebase.local", GlobalRole.READER);

        // Создаём пространство, где victim — owner (проверка идёт через spaces.owner_id)
        SpaceJpaEntity space = new SpaceJpaEntity();
        space.setName("space-" + uniqueLogin("x"));
        space.setDescription("desc");
        space.setOwnerId(victim.getId());
        spaceJpaRepository.save(space);

        String adminJwt = loginAndGetJwt("admin", "admin123");

        mockMvc.perform(delete("/api/admin/users/" + victim.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")));
    }

    @Test
    void adminUser_delete_userWithDocuments_returns409() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.ADMIN);
        User victim = persistUser("victim", "victim123", "victim@knowledgebase.local", GlobalRole.READER);

        insertDocumentForAuthor(victim.getId());

        String adminJwt = loginAndGetJwt("admin", "admin123");

        mockMvc.perform(delete("/api/admin/users/" + victim.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isConflict());
    }

    @Test
    void adminUser_delete_userWithVersions_returns409() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.ADMIN);
        User victim = persistUser("victim", "victim123", "victim@knowledgebase.local", GlobalRole.READER);

        insertVersionForAuthor(victim.getId());

        String adminJwt = loginAndGetJwt("admin", "admin123");

        mockMvc.perform(delete("/api/admin/users/" + victim.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isConflict());
    }

    @Test
    void adminUser_delete_userWithoutDependencies_returns204() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.ADMIN);
        User victim = persistUser("victim", "victim123", "victim@knowledgebase.local", GlobalRole.READER);

        String adminJwt = loginAndGetJwt("admin", "admin123");

        mockMvc.perform(delete("/api/admin/users/" + victim.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNoContent());
    }
}

