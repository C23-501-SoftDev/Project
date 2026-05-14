package com.knowledgebase.integration;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.infrastructure.persistence.entity.SpaceJpaEntity;
import com.knowledgebase.infrastructure.persistence.entity.UserJpaEntity;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminUserIntegrationTest extends IntegrationTestBase {

    @Test
    void adminUsers_crud_andPasswordFlow() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        String login = uniqueLogin("user");
        String email = login + "@example.com";

        // create (201)
        mockMvc.perform(post("/api/admin/users")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"login":"%s","email":"%s","password":"secret123","role":"READER","isAdmin":false}
                                """.formatted(login, email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.login", is(login)))
                .andExpect(jsonPath("$.email", is(email)))
                .andExpect(jsonPath("$.role", is("READER")))
                .andExpect(jsonPath("$.isAdmin", is(false)))
                .andExpect(jsonPath("$.isDeleted", is(false)));

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
                                {"login":"%s","email":"other_%s","password":"secret123","role":"READER","isAdmin":false}
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
    void adminUser_softDeleteAndRestore() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        String login = uniqueLogin("user");
        String email = login + "@example.com";

        // create user
        mockMvc.perform(post("/api/admin/users")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"login":"%s","email":"%s","password":"secret123","role":"READER","isAdmin":false}
                                """.formatted(login, email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.login", is(login)));

        // get user id from list
        String listResponse = mockMvc.perform(get("/api/admin/users?page=0&size=20")
                        .cookie(jwtCookie(adminJwt)))
                .andReturn().getResponse().getContentAsString();

        // soft-delete (200 with user data)
        mockMvc.perform(delete("/api/admin/users/2")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDeleted", is(true)));

        // authenticate deleted user - should fail (401)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isUnauthorized());

        // restore user (200)
        mockMvc.perform(post("/api/admin/users/2/restore")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDeleted", is(false)));

        // authenticate restored user - should succeed (200)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void adminUser_createWithIsAdminFlag() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        String login = uniqueLogin("adminuser");
        String email = login + "@example.com";

        // create user with isAdmin=true
        mockMvc.perform(post("/api/admin/users")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"login":"%s","email":"%s","password":"secret123","role":"EDITOR","isAdmin":true}
                                """.formatted(login, email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isAdmin", is(true)));

        // login and check can access admin API
        String newAdminJwt = loginAndGetJwt(login, "secret123");
        mockMvc.perform(get("/api/admin/users")
                        .cookie(jwtCookie(newAdminJwt)))
                .andExpect(status().isOk());
    }

    @Test
    void adminUser_updateRole_requiresReLoginForNewJwtRole() throws Exception {
        User admin = persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        User target = persistUser("editor", "editor123", "editor@knowledgebase.local", GlobalRole.EDITOR, false);

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
        mockMvc.perform(get("/api/admin/users").cookie(jwtCookie(editorJwtBefore)))
                .andExpect(status().isForbidden());

        // Перелогин — новый JWT выпускается с новой ролью.
        String editorJwtAfter = loginAndGetJwt(target.getLogin(), "editor123");
        mockMvc.perform(get("/api/admin/users").cookie(jwtCookie(editorJwtAfter)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUser_changePassword_affectsLogin() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        User user = persistUser("user1", "oldpass123", "user1@knowledgebase.local", GlobalRole.READER, false);

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
    void adminUser_deleteUserWithOwnedSpaces_softDeletes() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        User victim = persistUser("victim", "victim123", "victim@knowledgebase.local", GlobalRole.READER, false);

        // Создаём пространство, где victim — owner
        SpaceJpaEntity space = new SpaceJpaEntity();
        space.setName("space-" + uniqueLogin("x"));
        space.setDescription("desc");
        space.setOwnerId(victim.getId());
        spaceJpaRepository.save(space);

        String adminJwt = loginAndGetJwt("admin", "admin123");

        // Soft-delete теперь работает (200), даже если есть spaces
        mockMvc.perform(delete("/api/admin/users/" + victim.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDeleted", is(true)));
    }

    @Test
    void adminUser_deleteUserWithoutDependencies_softDeletes() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        User victim = persistUser("victim", "victim123", "victim@knowledgebase.local", GlobalRole.READER, false);

        String adminJwt = loginAndGetJwt("admin", "admin123");

        mockMvc.perform(delete("/api/admin/users/" + victim.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDeleted", is(true)));
    }

    @Test
    void adminUser_listWithIncludeDeleted() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        String login = uniqueLogin("user");
        String email = login + "@example.com";

        // create user
        mockMvc.perform(post("/api/admin/users")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"login":"%s","email":"%s","password":"secret123","role":"READER","isAdmin":false}
                                """.formatted(login, email)))
                .andExpect(status().isCreated());

        // soft-delete user
        mockMvc.perform(delete("/api/admin/users/2")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk());

        // без includeDeleted - только активные
        mockMvc.perform(get("/api/admin/users?page=0&size=20&includeDeleted=false")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].isDeleted", is(false)));

        // с includeDeleted=true - все пользователи
        mockMvc.perform(get("/api/admin/users?page=0&size=20&includeDeleted=true")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))));
    }
}
