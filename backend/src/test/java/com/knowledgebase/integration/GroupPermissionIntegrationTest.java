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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционные тесты прав групп на пространства (US4.2.2 + US4.1.9).
 *
 * Проверяют наследование прав: пользователь с ролью GUEST получает доступ
 * к пространству через членство в группе и теряет его при отзыве права,
 * исключении из группы или удалении группы.
 */
class GroupPermissionIntegrationTest extends IntegrationTestBase {

    private long createSpace(String adminJwt, String name) throws Exception {
        String resp = mockMvc.perform(post("/api/admin/spaces")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(resp.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));
    }

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

    private void addMember(String adminJwt, long groupId, Long userId) throws Exception {
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/members")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":%d}".formatted(userId)))
                .andExpect(status().isCreated());
    }

    private long grantGroupPermission(String adminJwt, long spaceId, long groupId, String type) throws Exception {
        String resp = mockMvc.perform(post("/api/admin/spaces/" + spaceId + "/group-permissions")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"groupId":%d,"permissionType":"%s"}
                                """.formatted(groupId, type)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(resp.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));
    }

    @Test
    void guest_getsAccessViaGroup_andLosesItOnRevoke() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        User guest = persistUser("guest", "guest123", "guest@knowledgebase.local", GlobalRole.GUEST);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        String guestJwt = loginAndGetJwt("guest", "guest123");

        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("gp"));
        long groupId = createGroup(adminJwt, "group-" + uniqueLogin("gp"));
        addMember(adminJwt, groupId, guest.getId());

        // До выдачи права GUEST не видит пространство и не имеет прав
        mockMvc.perform(get("/api/spaces").cookie(jwtCookie(guestJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(empty())));
        mockMvc.perform(get("/api/user/permissions?spaceId=" + spaceId).cookie(jwtCookie(guestJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRead", is(false)));

        // Выдаём группе право READ — участник группы получает доступ (US4.2.2, сценарий 1)
        long readPermId = grantGroupPermission(adminJwt, spaceId, groupId, "READ");

        mockMvc.perform(get("/api/spaces").cookie(jwtCookie(guestJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is((int) spaceId)));

        mockMvc.perform(get("/api/user/permissions?spaceId=" + spaceId).cookie(jwtCookie(guestJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRead", is(true)))
                .andExpect(jsonPath("$.canEdit", is(false)))
                .andExpect(jsonPath("$.permissions", contains("READ")));

        // Повторная выдача того же права — 409
        mockMvc.perform(post("/api/admin/spaces/" + spaceId + "/group-permissions")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"groupId":%d,"permissionType":"READ"}
                                """.formatted(groupId)))
                .andExpect(status().isConflict());

        // Выдаём WRITE (поглощает READ) — участник может редактировать (US4.2.2, сценарий 2)
        long writePermId = grantGroupPermission(adminJwt, spaceId, groupId, "WRITE");

        mockMvc.perform(get("/api/user/permissions?spaceId=" + spaceId).cookie(jwtCookie(guestJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRead", is(true)))
                .andExpect(jsonPath("$.canEdit", is(true)))
                .andExpect(jsonPath("$.canCreate", is(true)))
                .andExpect(jsonPath("$.permissions", contains("WRITE")));

        // Список прав групп пространства отражает текущее состояние (только WRITE)
        mockMvc.perform(get("/api/admin/spaces/" + spaceId + "/group-permissions")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].permissionType", is("WRITE")))
                .andExpect(jsonPath("$[0].groupName", notNullValue()));

        // Отзываем право — доступ пропадает (US4.2.2, сценарий 4)
        mockMvc.perform(delete("/api/admin/group-permissions/" + writePermId)
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/spaces").cookie(jwtCookie(guestJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(empty())));
        mockMvc.perform(get("/api/user/permissions?spaceId=" + spaceId).cookie(jwtCookie(guestJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRead", is(false)));
    }

    @Test
    void guest_losesAccess_whenRemovedFromGroup() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        User guest = persistUser("guest", "guest123", "guest@knowledgebase.local", GlobalRole.GUEST);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        String guestJwt = loginAndGetJwt("guest", "guest123");

        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("rm"));
        long groupId = createGroup(adminJwt, "group-" + uniqueLogin("rm"));
        addMember(adminJwt, groupId, guest.getId());
        grantGroupPermission(adminJwt, spaceId, groupId, "READ");

        mockMvc.perform(get("/api/user/permissions?spaceId=" + spaceId).cookie(jwtCookie(guestJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRead", is(true)));

        // Исключаем из группы — права, полученные через группу, пропадают (US4.1.9, сценарий 2)
        mockMvc.perform(delete("/api/admin/groups/" + groupId + "/members/" + guest.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/user/permissions?spaceId=" + spaceId).cookie(jwtCookie(guestJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRead", is(false)));
        mockMvc.perform(get("/api/spaces").cookie(jwtCookie(guestJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(empty())));
    }

    @Test
    void guest_losesAccess_whenGroupDeleted() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        User guest = persistUser("guest", "guest123", "guest@knowledgebase.local", GlobalRole.GUEST);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        String guestJwt = loginAndGetJwt("guest", "guest123");

        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("del"));
        long groupId = createGroup(adminJwt, "group-" + uniqueLogin("del"));
        addMember(adminJwt, groupId, guest.getId());
        grantGroupPermission(adminJwt, spaceId, groupId, "READ");

        mockMvc.perform(get("/api/user/permissions?spaceId=" + spaceId).cookie(jwtCookie(guestJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRead", is(true)));

        // Удаляем группу — права группы отзываются (US4.1.8, сценарий 2)
        mockMvc.perform(delete("/api/admin/groups/" + groupId)
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/user/permissions?spaceId=" + spaceId).cookie(jwtCookie(guestJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRead", is(false)));

        // Права группы удалены и из списка прав пространства
        mockMvc.perform(get("/api/admin/spaces/" + spaceId + "/group-permissions")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(empty())));
    }

    @Test
    void grantGroupPermission_missingSpaceOrGroup_returns404() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("404"));
        long groupId = createGroup(adminJwt, "group-" + uniqueLogin("404"));

        // Несуществующее пространство
        mockMvc.perform(post("/api/admin/spaces/999999/group-permissions")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"groupId":%d,"permissionType":"READ"}
                                """.formatted(groupId)))
                .andExpect(status().isNotFound());

        // Несуществующая группа
        mockMvc.perform(post("/api/admin/spaces/" + spaceId + "/group-permissions")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"groupId":999999,"permissionType":"READ"}
                                """))
                .andExpect(status().isNotFound());
    }
}
