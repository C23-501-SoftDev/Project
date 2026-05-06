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

class SpaceIntegrationTest extends IntegrationTestBase {

    @Test
    void admin_canCreateSpace_duplicateNameReturns409_andOwnerDefaultsToCurrentUser() throws Exception {
        User admin = persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.ADMIN);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        String spaceName = "space-" + uniqueLogin("kb");

        // ownerId отсутствует -> берём текущего пользователя
        mockMvc.perform(post("/api/admin/spaces")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc"}
                                """.formatted(spaceName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is(spaceName)))
                .andExpect(jsonPath("$.ownerId", is(admin.getId().intValue())));

        // duplicate (409)
        mockMvc.perform(post("/api/admin/spaces")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc"}
                                """.formatted(spaceName)))
                .andExpect(status().isConflict());
    }

    @Test
    void admin_grantsPermission_andUserSeesSpaceInMySpaces() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.ADMIN);
        User editor = persistUser("editor", "editor123", "editor@knowledgebase.local", GlobalRole.EDITOR);

        String adminJwt = loginAndGetJwt("admin", "admin123");
        String editorJwt = loginAndGetJwt("editor", "editor123");

        String spaceName = "space-" + uniqueLogin("kb");

        // create space
        String createResp = mockMvc.perform(post("/api/admin/spaces")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc","ownerId":null}
                                """.formatted(spaceName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // извлекаем id без json парсера: достаточно найти `"id":`
        long spaceId = Long.parseLong(createResp.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));

        // user initially has no spaces
        mockMvc.perform(get("/api/spaces").cookie(jwtCookie(editorJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(empty())));

        // grant READ
        mockMvc.perform(post("/api/admin/spaces/" + spaceId + "/permissions")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"READ"}
                                """.formatted(editor.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.spaceId", is((int) spaceId)))
                .andExpect(jsonPath("$.userId", is(editor.getId().intValue())))
                .andExpect(jsonPath("$.permissionType", is("READ")));

        // now editor sees space
        mockMvc.perform(get("/api/spaces").cookie(jwtCookie(editorJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is((int) spaceId)))
                .andExpect(jsonPath("$[0].name", is(spaceName)));
    }

    @Test
    void grantPermission_toNonExistingUser_orSpace_returns404() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.ADMIN);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        // non-existing space
        mockMvc.perform(post("/api/admin/spaces/999999/permissions")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":1,\"permissionType\":\"READ\"}"))
                .andExpect(status().isNotFound());

        // create space
        String spaceName = "space-" + uniqueLogin("kb");
        String createResp = mockMvc.perform(post("/api/admin/spaces")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc"}
                                """.formatted(spaceName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long spaceId = Long.parseLong(createResp.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));

        // non-existing user
        mockMvc.perform(post("/api/admin/spaces/" + spaceId + "/permissions")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":999999,\"permissionType\":\"READ\"}"))
                .andExpect(status().isNotFound());
    }
}

