package com.knowledgebase.integration;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционные тесты управления группами пользователей
 * (US4.1.8 — Создание и управление группами, US4.1.9 — Управление членством).
 */
class GroupIntegrationTest extends IntegrationTestBase {

    private long createGroup(String adminJwt, String name) throws Exception {
        String resp = mockMvc.perform(post("/api/admin/groups")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(resp.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));
    }

    @Test
    void admin_canManageGroups_CRUD() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        String name = "group-" + uniqueLogin("g");

        // Create (201)
        long groupId = createGroup(adminJwt, name);

        // Duplicate name (409) — US4.1.8, сценарий 1: название уникально
        mockMvc.perform(post("/api/admin/groups")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"another"}
                                """.formatted(name)))
                .andExpect(status().isConflict());

        // Get by id (200)
        mockMvc.perform(get("/api/admin/groups/" + groupId)
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(name)))
                .andExpect(jsonPath("$.description", is("desc")))
                .andExpect(jsonPath("$.memberCount", is(0)));

        // List (200)
        mockMvc.perform(get("/api/admin/groups")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(1)));

        // Update (200)
        String newName = name + "-updated";
        mockMvc.perform(put("/api/admin/groups/" + groupId)
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"new desc"}
                                """.formatted(newName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(newName)))
                .andExpect(jsonPath("$.description", is("new desc")));

        // Update to name taken by another group (409)
        long otherId = createGroup(adminJwt, "group-" + uniqueLogin("other"));
        mockMvc.perform(put("/api/admin/groups/" + otherId)
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"x"}
                                """.formatted(newName)))
                .andExpect(status().isConflict());

        // Delete (204) — US4.1.8, сценарий 2
        mockMvc.perform(delete("/api/admin/groups/" + groupId)
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNoContent());

        // Get after delete (404)
        mockMvc.perform(get("/api/admin/groups/" + groupId)
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNotFound());
    }

    @Test
    void groupMembers_addListRemove() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        User user = persistUser("member", "member123", "member@knowledgebase.local", GlobalRole.READER);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        long groupId = createGroup(adminJwt, "group-" + uniqueLogin("m"));

        // Add member (201) — US4.1.9, сценарий 1
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/members")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":%d}".formatted(user.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is(user.getId().intValue())))
                .andExpect(jsonPath("$.login", is("member")));

        // Duplicate add (409)
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/members")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":%d}".formatted(user.getId())))
                .andExpect(status().isConflict());

        // Add to non-existing group (404)
        mockMvc.perform(post("/api/admin/groups/999999/members")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":%d}".formatted(user.getId())))
                .andExpect(status().isNotFound());

        // Add non-existing user (404)
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/members")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":999999}"))
                .andExpect(status().isNotFound());

        // List members (200)
        mockMvc.perform(get("/api/admin/groups/" + groupId + "/members")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].login", is("member")))
                .andExpect(jsonPath("$[0].email", is("member@knowledgebase.local")));

        // memberCount отражается в данных группы
        mockMvc.perform(get("/api/admin/groups/" + groupId)
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount", is(1)));

        // Remove member (204) — US4.1.9, сценарий 2
        mockMvc.perform(delete("/api/admin/groups/" + groupId + "/members/" + user.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNoContent());

        // Remove again (409 — не состоит в группе)
        mockMvc.perform(delete("/api/admin/groups/" + groupId + "/members/" + user.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/admin/groups/" + groupId + "/members")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(empty())));
    }

    @Test
    void groups_areForbiddenForNonAdmins() throws Exception {
        persistUser("reader", "reader123", "reader@knowledgebase.local", GlobalRole.READER);
        String readerJwt = loginAndGetJwt("reader", "reader123");

        mockMvc.perform(get("/api/admin/groups").cookie(jwtCookie(readerJwt)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/groups")
                        .cookie(jwtCookie(readerJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"description\":\"y\"}"))
                .andExpect(status().isForbidden());
    }
}
