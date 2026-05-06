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
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.ADMIN);
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

        mockMvc.perform(get("/api/user/permissions?spaceId=" + spaceId)
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spaceId", is((int) spaceId)))
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("READ", "WRITE", "OWNER")))
                .andExpect(jsonPath("$.canRead", is(true)))
                .andExpect(jsonPath("$.canEdit", is(true)))
                .andExpect(jsonPath("$.canCreate", is(true)));
    }

    @Test
    void myPermissions_editorFlags_dependOnSpacePermissions() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.ADMIN);
        User editor = persistUser("editor", "editor123", "editor@knowledgebase.local", GlobalRole.EDITOR);

        String adminJwt = loginAndGetJwt("admin", "admin123");
        String editorJwt = loginAndGetJwt("editor", "editor123");

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
                        .cookie(jwtCookie(editorJwt)))
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
                                """.formatted(editor.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/user/permissions?spaceId=" + spaceId)
                        .cookie(jwtCookie(editorJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", contains("READ")))
                .andExpect(jsonPath("$.canRead", is(true)))
                .andExpect(jsonPath("$.canEdit", is(false)))
                .andExpect(jsonPath("$.canCreate", is(false)));

        // grant WRITE -> canEdit/canCreate true
        mockMvc.perform(post("/api/admin/spaces/" + spaceId + "/permissions")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"WRITE"}
                                """.formatted(editor.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/user/permissions?spaceId=" + spaceId)
                        .cookie(jwtCookie(editorJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("READ", "WRITE")))
                .andExpect(jsonPath("$.canRead", is(true)))
                .andExpect(jsonPath("$.canEdit", is(true)))
                .andExpect(jsonPath("$.canCreate", is(true)));
    }

    @Test
    void mySpaces_endpoint_returnsSpacesWithAnyPermission() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.ADMIN);
        User reader = persistUser("reader", "reader123", "reader@knowledgebase.local", GlobalRole.READER);

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

