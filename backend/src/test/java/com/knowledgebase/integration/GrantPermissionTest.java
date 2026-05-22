package com.knowledgebase.integration;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GrantPermissionTest extends IntegrationTestBase {

    @Test
    void grantPermission_nonExistentUser_returns404() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

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

        mockMvc.perform(post("/api/admin/spaces/" + spaceId + "/permissions")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":999999,"permissionType":"READ"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void grantPermission_existentUser_returns201() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        User user = persistUser("user", "user123", "user@knowledgebase.local", GlobalRole.READER);
        String adminJwt = loginAndGetJwt("admin", "admin123");

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

        mockMvc.perform(post("/api/admin/spaces/" + spaceId + "/permissions")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"READ"}
                                """.formatted(user.getId())))
                .andExpect(status().isCreated());
    }
}
