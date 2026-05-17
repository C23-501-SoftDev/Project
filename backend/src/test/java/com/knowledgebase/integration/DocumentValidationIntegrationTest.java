package com.knowledgebase.integration;

import com.knowledgebase.domain.model.DocumentStatus;
import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentValidationIntegrationTest extends IntegrationTestBase {

    @Test
    void createDocument_circularDependency_returns422() throws Exception {
        persistUser("user", "user123", "user@kb.local", GlobalRole.EDITOR, true);
        String jwt = loginAndGetJwt("user", "user123");
        long spaceId = createSpace("Space", "Desc");

        String docResp = mockMvc.perform(post("/api/documents")
                        .cookie(jwtCookie(jwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"Doc1\",\"spaceId\":" + spaceId + "}"))
                .andReturn().getResponse().getContentAsString();
        long docId = Long.parseLong(docResp.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));

        mockMvc.perform(post("/api/documents")
                        .cookie(jwtCookie(jwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"Doc2\",\"spaceId\":" + spaceId + ",\"parentId\":" + docId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/documents/" + docId)
                        .cookie(jwtCookie(jwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"parentId\":" + docId + "}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createDocument_duplicateTitle_returns422() throws Exception {
        persistUser("user", "user123", "user@kb.local", GlobalRole.EDITOR, true);
        String jwt = loginAndGetJwt("user", "user123");
        long spaceId = createSpace("Space", "Desc");

        mockMvc.perform(post("/api/documents")
                        .cookie(jwtCookie(jwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"Doc1\",\"spaceId\":" + spaceId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/documents")
                        .cookie(jwtCookie(jwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"Doc1\",\"spaceId\":" + spaceId + "}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private long createSpace(String name, String desc) throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        String spaceResp = mockMvc.perform(post("/api/admin/spaces")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"description\":\"" + desc + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(spaceResp.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));
    }
}
