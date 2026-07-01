package com.knowledgebase.integration;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentSearchIntegrationTest extends IntegrationTestBase {

    @Test
    void searchByTitle_returnsOnlyAccessibleDocuments() throws Exception {
        ensureDocumentsTable();
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        var guest = persistUser("guest", "guest123", "guest@kb.local", GlobalRole.GUEST);

        String adminJwt = loginAndGetJwt("admin", "admin123");
        String guestJwt = loginAndGetJwt("guest", "guest123");

        long visibleSpaceId = createSpace(adminJwt, "Visible Space");
        long hiddenSpaceId = createSpace(adminJwt, "Hidden Space");

        grantRead(adminJwt, visibleSpaceId, guest.getId());

        createDocument(adminJwt, visibleSpaceId, "Fast access guide");
        createDocument(adminJwt, hiddenSpaceId, "Fast secret memo");
        createDocument(adminJwt, visibleSpaceId, "Architecture notes");

        mockMvc.perform(get("/api/documents/search?q=Fast&page=0&size=10")
                        .cookie(jwtCookie(guestJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", is("Fast access guide")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void searchByTitle_withDateRange_returnsOnlyInRange() throws Exception {
        ensureDocumentsTable();
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        long spaceId = createSpace(adminJwt, "Date Filter Space");

        long oldDocId = createDocument(adminJwt, spaceId, "Fast old note");
        long inRangeDocId = createDocument(adminJwt, spaceId, "Fast fresh note");

        updateDocumentDates(oldDocId, LocalDateTime.of(2026, 1, 10, 10, 0), LocalDateTime.of(2026, 1, 10, 10, 0));
        updateDocumentDates(inRangeDocId, LocalDateTime.of(2026, 3, 10, 10, 0), LocalDateTime.of(2026, 3, 15, 10, 0));

        mockMvc.perform(get("/api/documents/search?q=Fast&dateFrom=2026-02-01&dateTo=2026-04-01&page=0&size=10")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", is("Fast fresh note")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void searchByDate_onlyWithoutQuery_returnsResults() throws Exception {
        ensureDocumentsTable();
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        long spaceId = createSpace(adminJwt, "Date Only Space");

        long oldDocId = createDocument(adminJwt, spaceId, "Architecture draft");
        long inRangeDocId = createDocument(adminJwt, spaceId, "Release checklist");

        updateDocumentDates(oldDocId, LocalDateTime.of(2026, 1, 10, 10, 0), LocalDateTime.of(2026, 1, 10, 10, 0));
        updateDocumentDates(inRangeDocId, LocalDateTime.of(2026, 3, 10, 10, 0), LocalDateTime.of(2026, 3, 15, 10, 0));

        mockMvc.perform(get("/api/documents/search?dateFrom=2026-02-01&dateTo=2026-04-01&page=0&size=10")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", is("Release checklist")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void searchByTitle_withInvalidDateRange_returnsValidationError() throws Exception {
        ensureDocumentsTable();
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        mockMvc.perform(get("/api/documents/search?q=Fast&dateFrom=2026-05-01&dateTo=2026-04-01")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Дата начала не может быть позже даты окончания")));
    }

    @Test
    void searchByTitle_withOnlyDateFromOrDateTo_appliesOneSidedFilter() throws Exception {
        ensureDocumentsTable();
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        long spaceId = createSpace(adminJwt, "One Side Space");

        long oldDocId = createDocument(adminJwt, spaceId, "Fast alpha");
        long freshDocId = createDocument(adminJwt, spaceId, "Fast beta");

        updateDocumentDates(oldDocId, LocalDateTime.of(2026, 1, 5, 12, 0), LocalDateTime.of(2026, 1, 5, 12, 0));
        updateDocumentDates(freshDocId, LocalDateTime.of(2026, 5, 5, 12, 0), LocalDateTime.of(2026, 5, 5, 12, 0));

        mockMvc.perform(get("/api/documents/search?q=Fast&dateFrom=2026-03-01&page=0&size=10")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", is("Fast beta")));

        mockMvc.perform(get("/api/documents/search?q=Fast&dateTo=2026-02-01&page=0&size=10")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", is("Fast alpha")));
    }

    @Test
    void searchPage_withoutMatches_showsEmptyMessage() throws Exception {
        ensureDocumentsTable();
        persistUser("admin", "admin123", "admin@kb.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        mockMvc.perform(get("/search?q=NothingToFind")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Документы не найдены")));
    }

    private long createSpace(String jwt, String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/spaces")
                        .cookie(jwtCookie(jwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return Long.parseLong(response.replaceAll("(?s).*\\\"id\\\"\\s*:\\s*(\\d+).*", "$1"));
    }

    private void ensureDocumentsTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS documents (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    title VARCHAR(500) NOT NULL,
                    git_file_path VARCHAR(500) NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    author_id BIGINT NOT NULL,
                    space_id BIGINT NOT NULL,
                    template_id BIGINT,
                    parent_document_id BIGINT,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uq_documents_git_file_path UNIQUE (git_file_path)
                )
                """);
        jdbcTemplate.execute("DELETE FROM documents");
    }

    private void grantRead(String adminJwt, long spaceId, Long userId) throws Exception {
        mockMvc.perform(post("/api/admin/spaces/" + spaceId + "/permissions")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"READ"}
                                """.formatted(userId)))
                .andExpect(status().isCreated());
    }

    private long createDocument(String jwt, long spaceId, String title) throws Exception {
        String response = mockMvc.perform(post("/api/documents")
                        .cookie(jwtCookie(jwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"%s","content":"# test","spaceId":%d}
                                """.formatted(title, spaceId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return Long.parseLong(response.replaceAll("(?s).*\\\"id\\\"\\s*:\\s*(\\d+).*", "$1"));
    }

    private void updateDocumentDates(long documentId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        jdbcTemplate.update("UPDATE documents SET created_at = ?, updated_at = ? WHERE id = ?", createdAt, updatedAt, documentId);
    }
}