package com.knowledgebase.integration;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PermissionIntegrationTest extends IntegrationTestBase {

    @Test
    void myPermissions_adminAlwaysHasAllFlags_andNonExistingSpaceReturns404() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        mockMvc.perform(get("/api/user/permissions?spaceId=999999")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNotFound());

        // create space
        String spaceName = "space-" + uniqueLogin("kb");
        String spaceResp = mockMvc.perform(post("/api/admin/spaces")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc"}
                                """.formatted(spaceName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long spaceId = Long.parseLong(spaceResp.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));

        // Создатель пространства получает OWNER, роль EDITOR даёт WRITE.
        // READ в списке не дублируется: PermissionService убирает его, когда есть WRITE/OWNER
        // (WRITE подразумевает чтение). Флаги для UI при этом остаются полными.
        mockMvc.perform(get("/api/user/permissions?spaceId=" + spaceId)
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spaceId", is((int) spaceId)))
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("WRITE", "OWNER")))
                .andExpect(jsonPath("$.canRead", is(true)))
                .andExpect(jsonPath("$.canEdit", is(true)))
                .andExpect(jsonPath("$.canCreate", is(true)));
    }

    @Test
    void myPermissions_guestFlags_dependOnSpacePermissions() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        // GUEST: доступ определяется только явными правами на пространство (US4.2.2)
        User guest = persistUser("guest", "guest123", "guest@knowledgebase.local", GlobalRole.GUEST);

        String adminJwt = loginAndGetJwt("admin", "admin123");
        String guestJwt = loginAndGetJwt("guest", "guest123");

        String spaceName = "space-" + uniqueLogin("kb");
        String spaceResp = mockMvc.perform(post("/api/admin/spaces")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc"}
                                """.formatted(spaceName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long spaceId = Long.parseLong(spaceResp.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));

        // no permissions -> canRead false/canEdit false
        mockMvc.perform(get("/api/user/permissions?spaceId=" + spaceId)
                        .cookie(jwtCookie(guestJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRead", is(false)))
                .andExpect(jsonPath("$.canEdit", is(false)))
                .andExpect(jsonPath("$.canCreate", is(false)))
                .andExpect(jsonPath("$.permissions", is(empty())));

        // grant READ -> canRead true, canEdit false
        mockMvc.perform(post("/api/admin/spaces/" + spaceId + "/permissions")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"READ"}
                                """.formatted(guest.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/user/permissions?spaceId=" + spaceId)
                        .cookie(jwtCookie(guestJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", contains("READ")))
                .andExpect(jsonPath("$.canRead", is(true)))
                .andExpect(jsonPath("$.canEdit", is(false)))
                .andExpect(jsonPath("$.canCreate", is(false)));

        // grant WRITE -> canEdit/canCreate true (WRITE поглощает READ)
        mockMvc.perform(post("/api/admin/spaces/" + spaceId + "/permissions")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"WRITE"}
                                """.formatted(guest.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/user/permissions?spaceId=" + spaceId)
                        .cookie(jwtCookie(guestJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", contains("WRITE")))
                .andExpect(jsonPath("$.canRead", is(true)))
                .andExpect(jsonPath("$.canEdit", is(true)))
                .andExpect(jsonPath("$.canCreate", is(true)));
    }

    @Test
    void mySpaces_endpoint_returnsSpacesWithAnyPermission() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        // GUEST: получает доступ к пространству только после явной выдачи права
        User reader = persistUser("reader", "reader123", "reader@knowledgebase.local", GlobalRole.GUEST);

        String adminJwt = loginAndGetJwt("admin", "admin123");
        String readerJwt = loginAndGetJwt("reader", "reader123");

        String spaceName = "space-" + uniqueLogin("kb");
        String spaceResp = mockMvc.perform(post("/api/admin/spaces")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc"}
                                """.formatted(spaceName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long spaceId = Long.parseLong(spaceResp.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));

        // grant READ
        mockMvc.perform(post("/api/admin/spaces/" + spaceId + "/permissions")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"READ"}
                                """.formatted(reader.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/user/spaces").cookie(jwtCookie(readerJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is((int) spaceId)))
                .andExpect(jsonPath("$[0].name", is(spaceName)));
    }
}

