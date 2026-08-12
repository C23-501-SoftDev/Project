package com.knowledgebase.integration;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentVersionIntegrationTest extends IntegrationTestBase {

    @Test
    void publishedDocumentSaveCreatesGitVersionMetadataForEditor() throws Exception {
        persistUser("editor", "editor123", "editor@kb.local", GlobalRole.EDITOR, true);
        String editorJwt = loginAndGetJwt("editor", "editor123");
        long spaceId = createSpace();
        String createResponse = mockMvc.perform(post("/api/documents")
                        .cookie(jwtCookie(editorJwt)).contentType(APPLICATION_JSON)
                        .content("{\"title\":\"Versioned\",\"content\":\"# First\",\"spaceId\":" + spaceId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long documentId = Long.parseLong(createResponse.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));

        mockMvc.perform(put("/api/documents/" + documentId)
                        .cookie(jwtCookie(editorJwt)).contentType(APPLICATION_JSON)
                        .content("{\"content\":\"# Second\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM versions WHERE document_id = ?", Integer.class, documentId);
        assertEquals(1, count);
        assertEquals("editor", jdbcTemplate.queryForObject("""
                SELECT u.login FROM versions v JOIN users u ON u.id = v.author_id WHERE v.document_id = ?
                """, String.class, documentId));
        assertEquals(40, jdbcTemplate.queryForObject("SELECT LENGTH(git_hash) FROM versions WHERE document_id = ?",
                Integer.class, documentId));

        mockMvc.perform(put("/api/documents/" + documentId)
                        .cookie(jwtCookie(editorJwt)).contentType(APPLICATION_JSON)
                        .content("{\"title\":\"Versioned renamed\"}"))
                .andExpect(status().isOk());
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM versions WHERE document_id = ?",
                Integer.class, documentId));
    }

    private long createSpace() throws Exception {
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        String response = mockMvc.perform(post("/api/admin/spaces")
                        .cookie(jwtCookie(adminJwt)).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Version Space\",\"description\":\"test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(response.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));
    }
}
