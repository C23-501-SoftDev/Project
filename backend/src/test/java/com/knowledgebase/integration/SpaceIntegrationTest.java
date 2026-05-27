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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SpaceIntegrationTest extends IntegrationTestBase {

    @Test
    void admin_canManageSpace_CRUD() throws Exception {
        User admin = persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        User newOwner = persistUser("newowner", "pass123", "owner@kb.local", GlobalRole.READER);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        String spaceName = "space-crud-" + uniqueLogin("kb");

        // 1. Create
        String createResp = mockMvc.perform(post("/api/admin/spaces")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"initial desc"}
                                """.formatted(spaceName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long spaceId = Long.parseLong(createResp.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));

        // 2. Get Details
        mockMvc.perform(get("/api/admin/spaces/" + spaceId)
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(spaceName)))
                .andExpect(jsonPath("$.description", is("initial desc")));

        // 3. Update (including owner change)
        String updatedName = spaceName + "-updated";
        mockMvc.perform(put("/api/admin/spaces/" + spaceId)
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "updated desc",
                                  "ownerId": %d
                                }
                                """.formatted(updatedName, newOwner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(updatedName)))
                .andExpect(jsonPath("$.description", is("updated desc")))
                .andExpect(jsonPath("$.ownerId", is(newOwner.getId().intValue())));

        // Verify permissions updated: new owner should have OWNER permission
        mockMvc.perform(get("/api/admin/spaces/" + spaceId + "/permissions")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId == %d && @.permissionType == 'OWNER')]".formatted(newOwner.getId()), not(empty())));

        // 4. Delete
        mockMvc.perform(delete("/api/admin/spaces/" + spaceId)
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNoContent());

        // Verify 404 after delete
        mockMvc.perform(get("/api/admin/spaces/" + spaceId)
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNotFound());
    }

    @Test
    void admin_canCreateSpace_duplicateNameReturns409_andOwnerDefaultsToCurrentUser() throws Exception {
        User admin = persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
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
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
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
    void mySpaces_searchFiltersByName_andReturnsEmptyListWhenNothingMatches() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        User reader = persistUser("reader", "reader123", "reader@knowledgebase.local", GlobalRole.READER);

        String adminJwt = loginAndGetJwt("admin", "admin123");
        String readerJwt = loginAndGetJwt("reader", "reader123");

        String alphaName = "Alpha Space " + uniqueLogin("kb");
        String betaName = "Beta Space " + uniqueLogin("kb");

        mockMvc.perform(post("/api/admin/spaces")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc"}
                                """.formatted(alphaName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/admin/spaces")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc"}
                                """.formatted(betaName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/spaces/search?q=alpha").cookie(jwtCookie(readerJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is(alphaName)));

        mockMvc.perform(get("/api/spaces/search?q=missing-space").cookie(jwtCookie(readerJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(empty())));
    }

    @Test
    void grantPermission_toNonExistingUser_orSpace_returns404() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
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

