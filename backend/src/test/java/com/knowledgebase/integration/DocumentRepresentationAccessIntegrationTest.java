package com.knowledgebase.integration;

import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentRepresentationAccessIntegrationTest extends IntegrationTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"html", "pdf", "docx"})
    void exportsRespectDraftVisibilityAndSpaceAccess(String format) throws Exception {
        Fixture f = fixture();
        String endpoint = "/api/documents/" + f.document.getId() + "/export/" + format;
        for (String denied : new String[] {f.readerJwt, f.editorJwt, f.guestJwt}) {
            mockMvc.perform(get(endpoint).cookie(jwtCookie(denied))).andExpect(status().isForbidden());
        }
        for (String allowed : new String[] {f.authorJwt, f.adminJwt}) {
            mockMvc.perform(get(endpoint).cookie(jwtCookie(allowed)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", containsString("attachment;")))
                    .andExpect(result -> assertTrue(result.getResponse().getContentAsByteArray().length > 0));
        }
        documentService.publishDocument(f.document.getId(), f.author.getId(), false);
        mockMvc.perform(get(endpoint).cookie(jwtCookie(f.readerJwt))).andExpect(status().isOk());
        mockMvc.perform(get(endpoint).cookie(jwtCookie(f.guestJwt))).andExpect(status().isForbidden());
    }

    @Test
    void versionsAndAttachmentsFollowTheSameDraftRule() throws Exception {
        Fixture f = fixture();
        long id = f.document.getId();
        String base = "/api/documents/" + id;
        byte[] bytes = "private attachment".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(multipart(base + "/attachments")
                        .file(new MockMultipartFile("files", "private.txt", "text/plain", bytes))
                        .cookie(jwtCookie(f.authorJwt)))
                .andExpect(status().isCreated());
        long attachmentId = attachmentRepository.findByDocumentId(id, false).get(0).getId();
        String download = "/api/attachments/" + attachmentId + "/download";
        for (String endpoint : new String[] {base, base + "/versions", base + "/attachments", download,
                base + "/diff?from=" + "a".repeat(40) + "&to=" + "b".repeat(40)}) {
            mockMvc.perform(get(endpoint).cookie(jwtCookie(f.readerJwt))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/documents/" + id + "/history").cookie(jwtCookie(f.readerJwt)))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get(download).cookie(jwtCookie(f.authorJwt)))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes));

        documentService.publishDocument(id, f.author.getId(), false);
        documentService.updateDocument(id, null, "Version one", null, f.author.getId());
        documentService.updateDocument(id, null, "Version two", null, f.author.getId());
        mockMvc.perform(get(base + "/versions").cookie(jwtCookie(f.readerJwt)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].gitHash").exists());
        var hashes = jdbcTemplate.queryForList(
                "SELECT git_hash FROM versions WHERE document_id = ? ORDER BY created_at", String.class, id);
        mockMvc.perform(get(base + "/diff").param("from", hashes.get(0)).param("to", hashes.get(1))
                        .cookie(jwtCookie(f.readerJwt))).andExpect(status().isOk());
        mockMvc.perform(get(base + "/attachments").cookie(jwtCookie(f.readerJwt)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].filename").value("private.txt"));
        mockMvc.perform(get(download).cookie(jwtCookie(f.readerJwt)))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes));
    }

    private Fixture fixture() throws Exception {
        User author = persistUser("author", "password123", "author@kb.local", GlobalRole.EDITOR, false);
        persistUser("admin", "password123", "admin@kb.local", GlobalRole.EDITOR, true);
        persistUser("reader", "password123", "reader@kb.local", GlobalRole.READER, false);
        persistUser("other-editor", "password123", "editor@kb.local", GlobalRole.EDITOR, false);
        persistUser("guest", "password123", "guest@kb.local", GlobalRole.GUEST, false);
        Space space = spaceRepository.save(Space.create("Draft representations", "", author.getId()));
        Document document = documentService.createDocument("Private draft", "# Private content", space.getId(),
                null, author.getId(), null);
        return new Fixture(author, document, loginAndGetJwt("author", "password123"),
                loginAndGetJwt("admin", "password123"), loginAndGetJwt("reader", "password123"),
                loginAndGetJwt("other-editor", "password123"), loginAndGetJwt("guest", "password123"));
    }

    private record Fixture(User author, Document document, String authorJwt, String adminJwt,
                           String readerJwt, String editorJwt, String guestJwt) {}
}
