package com.knowledgebase.integration;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentVersionDiffIntegrationTest extends IntegrationTestBase {

    @Test
    void readerCanCompareTwoVersionsOfSameDocument() throws Exception {
        persistUser("editor", "editor123", "editor@kb.local", GlobalRole.EDITOR, true);
        String jwt = loginAndGetJwt("editor", "editor123");
        long spaceId = createSpace(jwt);
        long documentId = createDocument(jwt, spaceId, "Versioned");
        updatePublished(jwt, documentId, "First\nRemoved\nLast");
        updatePublished(jwt, documentId, "First\nAdded\nLast");
        List<String> hashes = jdbcTemplate.queryForList(
                "SELECT git_hash FROM versions WHERE document_id = ? ORDER BY created_at", String.class, documentId);

        mockMvc.perform(get("/api/documents/" + documentId + "/versions").cookie(jwtCookie(jwt)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(hashes.get(0))))
                .andExpect(content().string(containsString(hashes.get(1))));

        mockMvc.perform(get("/api/documents/" + documentId + "/diff")
                        .param("from", hashes.get(0)).param("to", hashes.get(1)).cookie(jwtCookie(jwt)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("REMOVED")))
                .andExpect(content().string(containsString("ADDED")))
                .andExpect(content().string(containsString("Removed")))
                .andExpect(content().string(containsString("Added")));
    }

    @Test
    void rejectsSameVersionHash() throws Exception {
        persistUser("editor2", "editor123", "editor2@kb.local", GlobalRole.EDITOR, true);
        String jwt = loginAndGetJwt("editor2", "editor123");
        long documentId = createDocument(jwt, createSpace(jwt), "Same version");
        updatePublished(jwt, documentId, "Version one");
        String hash = jdbcTemplate.queryForObject("SELECT git_hash FROM versions WHERE document_id = ?", String.class, documentId);

        mockMvc.perform(get("/api/documents/" + documentId + "/diff")
                        .param("from", hash).param("to", hash).cookie(jwtCookie(jwt)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsEmptyLinesForDistinctVersionsWithSameDocumentText() throws Exception {
        persistUser("editor-same-content", "editor123", "same-content@kb.local", GlobalRole.EDITOR, true);
        String jwt = loginAndGetJwt("editor-same-content", "editor123");
        long documentId = createDocument(jwt, createSpace(jwt), "Same content");
        updatePublished(jwt, documentId, "Unchanged content");
        updatePublished(jwt, documentId, "Same content renamed", "Unchanged content");
        List<String> hashes = jdbcTemplate.queryForList(
                "SELECT git_hash FROM versions WHERE document_id = ? ORDER BY created_at", String.class, documentId);

        mockMvc.perform(get("/api/documents/" + documentId + "/diff")
                        .param("from", hashes.get(0)).param("to", hashes.get(1)).cookie(jwtCookie(jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines").isEmpty());
    }

    @Test
    void keepsUnchangedLineAsContextWhenOnlyANewLineIsAppended() throws Exception {
        persistUser("editor-append", "editor123", "append@kb.local", GlobalRole.EDITOR, true);
        String jwt = loginAndGetJwt("editor-append", "editor123");
        long documentId = createDocument(jwt, createSpace(jwt), "Append line");
        updatePublished(jwt, documentId, "ghbdt, hello, hello");
        updatePublished(jwt, documentId, "ghbdt, hello, hello\nHello");
        List<String> hashes = jdbcTemplate.queryForList(
                "SELECT git_hash FROM versions WHERE document_id = ? ORDER BY created_at", String.class, documentId);

        mockMvc.perform(get("/api/documents/" + documentId + "/diff")
                        .param("from", hashes.get(0)).param("to", hashes.get(1)).cookie(jwtCookie(jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].type").value("CONTEXT"))
                .andExpect(jsonPath("$.lines[0].content").value("ghbdt, hello, hello"))
                .andExpect(jsonPath("$.lines[1].type").value("ADDED"))
                .andExpect(jsonPath("$.lines[1].content").value("Hello"));
    }

    @Test
    void returnsWholeFileWhenAllContextIsRequested() throws Exception {
        persistUser("editor-full-file", "editor123", "full-file@kb.local", GlobalRole.EDITOR, true);
        String jwt = loginAndGetJwt("editor-full-file", "editor123");
        long documentId = createDocument(jwt, createSpace(jwt), "Full file");
        updatePublished(jwt, documentId, numberedLines("line", 8));
        updatePublished(jwt, documentId, numberedLinesWithChange("line", 8, 4, "changed-4"));
        List<String> hashes = jdbcTemplate.queryForList(
                "SELECT git_hash FROM versions WHERE document_id = ? ORDER BY created_at", String.class, documentId);

        mockMvc.perform(get("/api/documents/" + documentId + "/diff")
                        .param("from", hashes.get(0)).param("to", hashes.get(1))
                        .param("context", "all").cookie(jwtCookie(jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(9));
    }

    @Test
    void acceptsUppercaseVersionHashes() throws Exception {
        persistUser("editor-uppercase", "editor123", "uppercase@kb.local", GlobalRole.EDITOR, true);
        String jwt = loginAndGetJwt("editor-uppercase", "editor123");
        long documentId = createDocument(jwt, createSpace(jwt), "Uppercase hashes");
        updatePublished(jwt, documentId, "First uppercase version");
        updatePublished(jwt, documentId, "Second uppercase version");
        List<String> hashes = jdbcTemplate.queryForList(
                "SELECT git_hash FROM versions WHERE document_id = ? ORDER BY created_at", String.class, documentId);

        mockMvc.perform(get("/api/documents/" + documentId + "/diff")
                        .param("from", hashes.get(0).toUpperCase()).param("to", hashes.get(1).toUpperCase())
                        .cookie(jwtCookie(jwt)))
                .andExpect(status().isOk());
    }

    @Test
    void comparesHistoricalVersionsAfterLaterDocumentRename() throws Exception {
        persistUser("editor-historical-path", "editor123", "historical-path@kb.local", GlobalRole.EDITOR, true);
        String jwt = loginAndGetJwt("editor-historical-path", "editor123");
        long documentId = createDocument(jwt, createSpace(jwt), "First title");
        updatePublished(jwt, documentId, "First title", "Before rename");
        updatePublished(jwt, documentId, "Second title", "Changed after rename");
        updatePublished(jwt, documentId, "Third title", "Changed after another rename");
        List<String> hashes = jdbcTemplate.queryForList(
                "SELECT git_hash FROM versions WHERE document_id = ? ORDER BY created_at", String.class, documentId);

        mockMvc.perform(get("/api/documents/" + documentId + "/diff")
                        .param("from", hashes.get(0)).param("to", hashes.get(1)).cookie(jwtCookie(jwt)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Before rename")))
                .andExpect(content().string(containsString("Changed after rename")));
    }

    @Test
    void doesNotRevealVersionRegisteredForAnotherDocument() throws Exception {
        persistUser("editor3", "editor123", "editor3@kb.local", GlobalRole.EDITOR, true);
        String jwt = loginAndGetJwt("editor3", "editor123");
        long spaceId = createSpace(jwt);
        long firstDocumentId = createDocument(jwt, spaceId, "First document");
        long secondDocumentId = createDocument(jwt, spaceId, "Second document");
        updatePublished(jwt, firstDocumentId, "First version");
        updatePublished(jwt, secondDocumentId, "Second version");
        String firstHash = jdbcTemplate.queryForObject("SELECT git_hash FROM versions WHERE document_id = ?", String.class, firstDocumentId);
        String foreignHash = jdbcTemplate.queryForObject("SELECT git_hash FROM versions WHERE document_id = ?", String.class, secondDocumentId);

        mockMvc.perform(get("/api/documents/" + firstDocumentId + "/diff")
                        .param("from", firstHash).param("to", foreignHash).cookie(jwtCookie(jwt)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Second version"))));
    }

    @Test
    void deniesDiffToGuestWithoutDocumentReadAccess() throws Exception {
        persistUser("editor4", "editor123", "editor4@kb.local", GlobalRole.EDITOR, true);
        persistUser("guest", "guest123", "guest@kb.local", GlobalRole.GUEST, false);
        String editorJwt = loginAndGetJwt("editor4", "editor123");
        String guestJwt = loginAndGetJwt("guest", "guest123");
        long documentId = createDocument(editorJwt, createSpace(editorJwt), "Restricted");
        updatePublished(editorJwt, documentId, "Restricted first");
        updatePublished(editorJwt, documentId, "Restricted second");
        List<String> hashes = jdbcTemplate.queryForList(
                "SELECT git_hash FROM versions WHERE document_id = ? ORDER BY created_at", String.class, documentId);

        mockMvc.perform(get("/api/documents/" + documentId + "/diff")
                        .param("from", hashes.get(0)).param("to", hashes.get(1)).cookie(jwtCookie(guestJwt)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsDiffThatExceedsConfiguredLineLimit() throws Exception {
        persistUser("editor5", "editor123", "editor5@kb.local", GlobalRole.EDITOR, true);
        String jwt = loginAndGetJwt("editor5", "editor123");
        long documentId = createDocument(jwt, createSpace(jwt), "Large diff");
        updatePublished(jwt, documentId, lines("before", 2001));
        updatePublished(jwt, documentId, lines("after", 2001));
        List<String> hashes = jdbcTemplate.queryForList(
                "SELECT git_hash FROM versions WHERE document_id = ? ORDER BY created_at", String.class, documentId);

        mockMvc.perform(get("/api/documents/" + documentId + "/diff")
                        .param("from", hashes.get(0)).param("to", hashes.get(1)).cookie(jwtCookie(jwt)))
                .andExpect(status().isUnprocessableEntity());
    }

    private long createSpace(String jwt) throws Exception {
        String response = mockMvc.perform(post("/api/admin/spaces").cookie(jwtCookie(jwt))
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Diff Space\"}"))
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
                        .content("{\"content\":\"" + content.replace("\n", "\\n") + "\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());
    }

    private void updatePublished(String jwt, long documentId, String title, String content) throws Exception {
        mockMvc.perform(put("/api/documents/" + documentId).cookie(jwtCookie(jwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"content\":\""
                                + content.replace("\n", "\\n") + "\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());
    }

    private String lines(String prefix, int count) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < count; index++) {
            builder.append(prefix).append('-').append(index).append('\n');
        }
        return builder.toString();
    }

    private String numberedLines(String prefix, int count) {
        return numberedLinesWithChange(prefix, count, -1, null);
    }

    private String numberedLinesWithChange(String prefix, int count, int changedIndex, String changedLine) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < count; index++) {
            builder.append(index == changedIndex ? changedLine : prefix + '-' + index).append('\n');
        }
        return builder.toString();
    }
}
