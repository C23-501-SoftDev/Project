package com.knowledgebase.integration;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.infrastructure.repository.git.JGitDocumentContentRepository;
import com.knowledgebase.support.IntegrationTestBase;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentVersionRollbackIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JGitDocumentContentRepository gitRepository;

    @Test
    void editorRestoresHistoricalTextAsNewVersion() throws Exception {
        persistUser("rollback-editor", "editor123", "rollback-editor@kb.local", GlobalRole.EDITOR, true);
        String jwt = loginAndGetJwt("rollback-editor", "editor123");
        long documentId = createDocument(jwt, createSpace(jwt), "Rollback document");
        updatePublished(jwt, documentId, "First text");
        updatePublished(jwt, documentId, "Second text");
        List<String> hashes = jdbcTemplate.queryForList(
                "SELECT git_hash FROM versions WHERE document_id = ? ORDER BY created_at", String.class, documentId);
        String selectedHash = hashes.get(0);

        String response = mockMvc.perform(post("/api/documents/" + documentId + "/versions/" + selectedHash + "/restore")
                        .cookie(jwtCookie(jwt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comment").value("Restore document version: " + selectedHash))
                .andReturn().getResponse().getContentAsString();
        String restoredHash = response.replaceAll("(?s).*\"gitHash\"\\s*:\\s*\"([0-9a-f]{40})\".*", "$1");

        mockMvc.perform(get("/api/documents/" + documentId).cookie(jwtCookie(jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("First text"));
        List<String> history = jdbcTemplate.queryForList(
                "SELECT git_hash FROM versions WHERE document_id = ? ORDER BY created_at", String.class, documentId);
        assertEquals(3, history.size());
        org.junit.jupiter.api.Assertions.assertNotEquals(selectedHash, restoredHash);
        org.junit.jupiter.api.Assertions.assertTrue(history.contains(selectedHash));
        org.junit.jupiter.api.Assertions.assertTrue(history.contains(restoredHash));
        String currentPath = jdbcTemplate.queryForObject(
                "SELECT git_file_path FROM documents WHERE id = ?", String.class, documentId);
        assertEquals("First text", gitRepository.readDocumentVersion(currentPath, restoredHash).orElseThrow());
        assertEquals("{\n  \"documentId\": " + documentId + ",\n  \"title\": \"Rollback document\",\n"
                        + "  \"status\": \"PUBLISHED\",\n  \"parentDocumentId\": null\n}\n",
                gitRepository.readDocumentVersion(".metadata/documents/" + documentId + ".json", restoredHash).orElseThrow());
        try (Git git = Git.open(tempDir.resolve("git-repo").toFile())) {
            assertEquals(restoredHash, git.log().setMaxCount(1).call().iterator().next().getId().name());
        }
    }

    @Test
    void readerCannotRestoreVersion() throws Exception {
        persistUser("rollback-owner", "editor123", "rollback-owner@kb.local", GlobalRole.EDITOR, true);
        persistUser("rollback-reader", "reader123", "rollback-reader@kb.local", GlobalRole.READER, false);
        String ownerJwt = loginAndGetJwt("rollback-owner", "editor123");
        String readerJwt = loginAndGetJwt("rollback-reader", "reader123");
        long documentId = createDocument(ownerJwt, createSpace(ownerJwt), "Protected rollback");
        updatePublished(ownerJwt, documentId, "Protected text");
        String hash = jdbcTemplate.queryForObject("SELECT git_hash FROM versions WHERE document_id = ?", String.class, documentId);

        mockMvc.perform(post("/api/documents/" + documentId + "/versions/" + hash + "/restore")
                        .cookie(jwtCookie(readerJwt)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMalformedAndForeignVersionHashes() throws Exception {
        persistUser("rollback-validation", "editor123", "rollback-validation@kb.local", GlobalRole.EDITOR, true);
        String jwt = loginAndGetJwt("rollback-validation", "editor123");
        long spaceId = createSpace(jwt);
        long firstDocumentId = createDocument(jwt, spaceId, "First validation document");
        long secondDocumentId = createDocument(jwt, spaceId, "Second validation document");
        updatePublished(jwt, firstDocumentId, "First text");
        updatePublished(jwt, secondDocumentId, "Second text");
        String foreignHash = jdbcTemplate.queryForObject(
                "SELECT git_hash FROM versions WHERE document_id = ?", String.class, secondDocumentId);

        mockMvc.perform(post("/api/documents/" + firstDocumentId + "/versions/not-a-hash/restore")
                        .cookie(jwtCookie(jwt)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/documents/" + firstDocumentId + "/versions/" + foreignHash + "/restore")
                        .cookie(jwtCookie(jwt)))
                .andExpect(status().isNotFound());
    }

    private long createSpace(String jwt) throws Exception {
        String response = mockMvc.perform(post("/api/admin/spaces").cookie(jwtCookie(jwt))
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Rollback Space\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(response.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));
    }

    private long createDocument(String jwt, long spaceId, String title) throws Exception {
        String response = mockMvc.perform(post("/api/documents").cookie(jwtCookie(jwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"content\":\"Initial\",\"spaceId\":" + spaceId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(response.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));
    }

    private void updatePublished(String jwt, long documentId, String content) throws Exception {
        mockMvc.perform(put("/api/documents/" + documentId).cookie(jwtCookie(jwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());
    }
}
