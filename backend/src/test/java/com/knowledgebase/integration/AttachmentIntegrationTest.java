package com.knowledgebase.integration;

import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttachmentIntegrationTest extends IntegrationTestBase {

    @Test
    void uploadAttachment_createsFileAndMetadata_andListReturnsIt() throws Exception {
        TestContext context = createDocumentContext();

        MockMultipartFile file = new MockMultipartFile(
                "files",
                "specification.txt",
                "text/plain",
                "hello attachment".getBytes());

        mockMvc.perform(multipart("/api/documents/" + context.document.getId() + "/attachments")
                        .file(file)
                        .cookie(jwtCookie(context.jwt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].filename", is("specification.txt")))
                .andExpect(jsonPath("$[0].documentId", is(context.document.getId().intValue())));

        assertEquals(1, attachmentRepository.findByDocumentId(context.document.getId(), false).size());

        String storagePath = attachmentRepository.findByDocumentId(context.document.getId(), false).get(0).getStoragePath();
        assertTrue(Files.exists(tempDir.resolve("blob-storage").resolve(storagePath)));

        mockMvc.perform(get("/api/documents/" + context.document.getId() + "/attachments")
                        .cookie(jwtCookie(context.jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename", is("specification.txt")));
    }

    @Test
    void deleteAttachment_removesFileAndMetadata() throws Exception {
        TestContext context = createDocumentContext();

        MockMultipartFile file = new MockMultipartFile(
                "files",
                "diagram.png",
                "image/png",
                new byte[] {1, 2, 3, 4});

        mockMvc.perform(multipart("/api/documents/" + context.document.getId() + "/attachments")
                        .file(file)
                        .cookie(jwtCookie(context.jwt)))
                .andExpect(status().isCreated());

        var attachments = attachmentRepository.findByDocumentId(context.document.getId(), false);
        long attachmentId = attachments.get(0).getId();
        String storagePath = attachments.get(0).getStoragePath();

        mockMvc.perform(delete("/api/documents/" + context.document.getId() + "/attachments/" + attachmentId)
                        .cookie(jwtCookie(context.jwt)))
                .andExpect(status().isNoContent());

        assertTrue(attachmentRepository.findByDocumentId(context.document.getId(), false).isEmpty());
        assertTrue(Files.notExists(tempDir.resolve("blob-storage").resolve(storagePath)));
    }

    @Test
    void uploadAttachment_tooLargeFile_returns422() throws Exception {
        TestContext context = createDocumentContext();

        MockMultipartFile file = new MockMultipartFile(
                "files",
                "huge.txt",
                "text/plain",
                new byte[10 * 1024 * 1024 + 1]);

        mockMvc.perform(multipart("/api/documents/" + context.document.getId() + "/attachments")
                        .file(file)
                        .cookie(jwtCookie(context.jwt)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("Максимум")));
    }

    @Test
    void uploadAttachment_forbiddenExtension_returns422() throws Exception {
        TestContext context = createDocumentContext();

        MockMultipartFile file = new MockMultipartFile(
                "files",
                "virus.exe",
                "application/octet-stream",
                new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/documents/" + context.document.getId() + "/attachments")
                        .file(file)
                        .cookie(jwtCookie(context.jwt)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("Допустимые типы")));
    }

        private TestContext createDocumentContext() throws Exception {
                String login = uniqueLogin("editor");
                User user = persistUser(login, "user123", login + "@kb.local", GlobalRole.EDITOR, false);
        String jwt = loginAndGetJwt(user.getLogin(), "user123");
        Space space = spaceRepository.save(Space.create("Attachments Space", "Space for attachments", user.getId()));
                createDocumentsTableForAttachmentTest();
        Document document = documentService.createDocument("Attachment doc", "# content", space.getId(), null, user.getId(), null);
        return new TestContext(jwt, document);
    }

        private void createDocumentsTableForAttachmentTest() {
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
                                        created_at TIMESTAMP NOT NULL,
                                        updated_at TIMESTAMP NOT NULL
                                )
                                """);
        }

    private record TestContext(String jwt, Document document) {}
}
