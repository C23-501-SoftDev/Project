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
 * Интеграционные тесты механизма назначения прав через REST API.
 *
 * Покрывают эндпоинты под {@code /api/admin/spaces/{spaceId}/permission-assignments}
 * по всем сценариям: happy path, ошибки валидации, защиту последнего OWNER,
 * проверку ролей и работу с удалёнными пользователями.
 */
class SpacePermissionAssignmentIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/admin/spaces/";

    private long createSpace(String adminJwt, String name) throws Exception {
        String spaceResp = mockMvc.perform(post("/api/admin/spaces")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(spaceResp.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // POST: assign permission
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void admin_assignsPermission_returns201_andResponseHasUserDetails() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        User editor = persistUser("editor", "editor123", "editor@kb.local", GlobalRole.EDITOR);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        mockMvc.perform(post(BASE + spaceId + "/permission-assignments")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"READ"}
                                """.formatted(editor.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permissionId", notNullValue()))
                .andExpect(jsonPath("$.spaceId", is((int) spaceId)))
                .andExpect(jsonPath("$.userId", is(editor.getId().intValue())))
                .andExpect(jsonPath("$.userLogin", is("editor")))
                .andExpect(jsonPath("$.userEmail", is("editor@kb.local")))
                .andExpect(jsonPath("$.permissionType", is("READ")))
                .andExpect(jsonPath("$.grantedAt", notNullValue()));
    }

    @Test
    void assign_toNonExistingSpace_returns404() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        User editor = persistUser("editor", "editor123", "editor@kb.local", GlobalRole.EDITOR);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        mockMvc.perform(post(BASE + "999999/permission-assignments")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"READ"}
                                """.formatted(editor.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void assign_toNonExistingUser_returns404() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        mockMvc.perform(post(BASE + spaceId + "/permission-assignments")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":999999,\"permissionType\":\"READ\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void assign_duplicatePermission_returns409() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        User editor = persistUser("editor", "editor123", "editor@kb.local", GlobalRole.EDITOR);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        // первое назначение
        mockMvc.perform(post(BASE + spaceId + "/permission-assignments")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"READ"}
                                """.formatted(editor.getId())))
                .andExpect(status().isCreated());

        // повторное → 409
        mockMvc.perform(post(BASE + spaceId + "/permission-assignments")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"READ"}
                                """.formatted(editor.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void assign_invalidPayload_returns400() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        mockMvc.perform(post(BASE + spaceId + "/permission-assignments")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":null,\"permissionType\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assign_withoutAdminRole_returns403() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        User editor = persistUser("editor", "editor123", "editor@kb.local", GlobalRole.EDITOR);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        String editorJwt = loginAndGetJwt("editor", "editor123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        mockMvc.perform(post(BASE + spaceId + "/permission-assignments")
                        .cookie(jwtCookie(editorJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"READ"}
                                """.formatted(editor.getId())))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET: list assignments
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void listForSpace_returnsAllAssignments() throws Exception {
        User admin = persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        User editor = persistUser("editor", "editor123", "editor@kb.local", GlobalRole.EDITOR);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        // Назначаем READ редактору
        mockMvc.perform(post(BASE + spaceId + "/permission-assignments")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"READ"}
                                """.formatted(editor.getId())))
                .andExpect(status().isCreated());

        // Список содержит OWNER (от создания пространства) + READ
        mockMvc.perform(get(BASE + spaceId + "/permission-assignments")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[?(@.userId == %d && @.permissionType == 'OWNER')]"
                        .formatted(admin.getId()), not(empty())))
                .andExpect(jsonPath("$[?(@.userId == %d && @.permissionType == 'READ')]"
                        .formatted(editor.getId()), not(empty())));
    }

    @Test
    void listForSpace_nonExistingSpace_returns404() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        mockMvc.perform(get(BASE + "999999/permission-assignments")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listForUser_returnsUserPermissions() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        User editor = persistUser("editor", "editor123", "editor@kb.local", GlobalRole.EDITOR);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        mockMvc.perform(post(BASE + spaceId + "/permission-assignments")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"WRITE"}
                                """.formatted(editor.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get(BASE + spaceId + "/permission-assignments/users/" + editor.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].permissionType", is("WRITE")))
                .andExpect(jsonPath("$[0].userId", is(editor.getId().intValue())));
    }

    @Test
    void listForUser_nonExistingUser_returns404() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        mockMvc.perform(get(BASE + spaceId + "/permission-assignments/users/999999")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listForUser_returnsEmptyWhenNoPermissions() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        User editor = persistUser("editor", "editor123", "editor@kb.local", GlobalRole.EDITOR);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        mockMvc.perform(get(BASE + spaceId + "/permission-assignments/users/" + editor.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ─────────────────────────────────────────────────────────────────────
    // PUT: change permission type
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void changeType_changesReadToWrite() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        User editor = persistUser("editor", "editor123", "editor@kb.local", GlobalRole.EDITOR);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        String assignResp = mockMvc.perform(post(BASE + spaceId + "/permission-assignments")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"READ"}
                                """.formatted(editor.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long permissionId = Long.parseLong(
                assignResp.replaceAll("(?s).*\"permissionId\"\\s*:\\s*(\\d+).*", "$1"));

        mockMvc.perform(put(BASE + spaceId + "/permission-assignments/" + permissionId)
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"permissionType\":\"WRITE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionType", is("WRITE")))
                .andExpect(jsonPath("$.spaceId", is((int) spaceId)))
                .andExpect(jsonPath("$.userId", is(editor.getId().intValue())));
    }

    @Test
    void changeType_nonExistingPermission_returns404() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        mockMvc.perform(put(BASE + spaceId + "/permission-assignments/999999")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"permissionType\":\"WRITE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void changeType_sameTypeAsCurrent_returns422() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        User editor = persistUser("editor", "editor123", "editor@kb.local", GlobalRole.EDITOR);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        String assignResp = mockMvc.perform(post(BASE + spaceId + "/permission-assignments")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"READ"}
                                """.formatted(editor.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long permissionId = Long.parseLong(
                assignResp.replaceAll("(?s).*\"permissionId\"\\s*:\\s*(\\d+).*", "$1"));

        mockMvc.perform(put(BASE + spaceId + "/permission-assignments/" + permissionId)
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"permissionType\":\"READ\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void changeType_demoteLastOwner_returns409() throws Exception {
        User admin = persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        // Найти permissionId для OWNER (админ)
        String list = mockMvc.perform(get(BASE + spaceId + "/permission-assignments")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long ownerPermissionId = Long.parseLong(
                list.replaceAll("(?s).*?\"permissionId\"\\s*:\\s*(\\d+)[^}]*\"permissionType\"\\s*:\\s*\"OWNER\".*",
                        "$1"));

        // Понизить единственного OWNER до READ → 409
        mockMvc.perform(put(BASE + spaceId + "/permission-assignments/" + ownerPermissionId)
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"permissionType\":\"READ\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void changeType_permissionFromAnotherSpace_returns404() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        User editor = persistUser("editor", "editor123", "editor@kb.local", GlobalRole.EDITOR);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long space1 = createSpace(adminJwt, "space-1-" + uniqueLogin("kb"));
        long space2 = createSpace(adminJwt, "space-2-" + uniqueLogin("kb"));

        String assignResp = mockMvc.perform(post(BASE + space1 + "/permission-assignments")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"READ"}
                                """.formatted(editor.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long permissionId = Long.parseLong(
                assignResp.replaceAll("(?s).*\"permissionId\"\\s*:\\s*(\\d+).*", "$1"));

        // Попытка изменить через URL другого пространства
        mockMvc.perform(put(BASE + space2 + "/permission-assignments/" + permissionId)
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"permissionType\":\"WRITE\"}"))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────────────
    // DELETE: revoke single permission
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void revoke_existingPermission_returns204() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        User editor = persistUser("editor", "editor123", "editor@kb.local", GlobalRole.EDITOR);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        String assignResp = mockMvc.perform(post(BASE + spaceId + "/permission-assignments")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"READ"}
                                """.formatted(editor.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long permissionId = Long.parseLong(
                assignResp.replaceAll("(?s).*\"permissionId\"\\s*:\\s*(\\d+).*", "$1"));

        mockMvc.perform(delete(BASE + spaceId + "/permission-assignments/" + permissionId)
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNoContent());

        // Подтверждаем, что в списке прав больше нет
        mockMvc.perform(get(BASE + spaceId + "/permission-assignments/users/" + editor.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void revoke_nonExistingPermission_returns404() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        mockMvc.perform(delete(BASE + spaceId + "/permission-assignments/999999")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNotFound());
    }

    @Test
    void revoke_lastOwner_returns409() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        String list = mockMvc.perform(get(BASE + spaceId + "/permission-assignments")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long ownerPermissionId = Long.parseLong(
                list.replaceAll("(?s).*?\"permissionId\"\\s*:\\s*(\\d+)[^}]*\"permissionType\"\\s*:\\s*\"OWNER\".*",
                        "$1"));

        mockMvc.perform(delete(BASE + spaceId + "/permission-assignments/" + ownerPermissionId)
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isConflict());
    }

    // ─────────────────────────────────────────────────────────────────────
    // DELETE: revoke all permissions for user
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void revokeAllForUser_revokesAllPermissions() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        User editor = persistUser("editor", "editor123", "editor@kb.local", GlobalRole.EDITOR);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        // Назначим READ и WRITE
        mockMvc.perform(post(BASE + spaceId + "/permission-assignments")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"READ"}
                                """.formatted(editor.getId())))
                .andExpect(status().isCreated());
        mockMvc.perform(post(BASE + spaceId + "/permission-assignments")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"WRITE"}
                                """.formatted(editor.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(BASE + spaceId + "/permission-assignments/users/" + editor.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spaceId", is((int) spaceId)))
                .andExpect(jsonPath("$.userId", is(editor.getId().intValue())))
                .andExpect(jsonPath("$.status", is("revoked")));

        mockMvc.perform(get(BASE + spaceId + "/permission-assignments/users/" + editor.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void revokeAllForUser_noPermissions_returns404() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        User editor = persistUser("editor", "editor123", "editor@kb.local", GlobalRole.EDITOR);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        mockMvc.perform(delete(BASE + spaceId + "/permission-assignments/users/" + editor.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokeAllForUser_nonExistingSpace_returns404() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        User editor = persistUser("editor", "editor123", "editor@kb.local", GlobalRole.EDITOR);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        mockMvc.perform(delete(BASE + "999999/permission-assignments/users/" + editor.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokeAllForUser_nonExistingUser_returns404() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        mockMvc.perform(delete(BASE + spaceId + "/permission-assignments/users/999999")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokeAllForUser_lastOwner_returns409() throws Exception {
        User admin = persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("kb"));

        // Админ — единственный OWNER, попытка удалить все его права → 409
        mockMvc.perform(delete(BASE + spaceId + "/permission-assignments/users/" + admin.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isConflict());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Common: auth checks
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void allEndpoints_requireAuthentication() throws Exception {
        long spaceId = 1L;

        mockMvc.perform(get(BASE + spaceId + "/permission-assignments"))
                .andExpect(status().is(anyOf(is(401), is(403))));

        mockMvc.perform(post(BASE + spaceId + "/permission-assignments")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":1,\"permissionType\":\"READ\"}"))
                .andExpect(status().is(anyOf(is(401), is(403))));

        mockMvc.perform(delete(BASE + spaceId + "/permission-assignments/1"))
                .andExpect(status().is(anyOf(is(401), is(403))));
    }
}
