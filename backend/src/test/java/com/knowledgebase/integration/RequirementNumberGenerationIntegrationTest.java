package com.knowledgebase.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequirementNumberGenerationIntegrationTest extends IntegrationTestBase {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createDocumentFromTemplate_assignsSequentialRequirementNumbersPerSpaceAndTemplate() {
        User author = persistUser(uniqueLogin("editor"), "user123", "editor@kb.local", GlobalRole.EDITOR, false);
        createDocumentsTable();

        Long templateId = createRequirementTemplate();

        Space spaceA = spaceRepository.save(Space.create("Space A", "First space", author.getId()));
        Document firstDocument = documentService.createDocument("Doc A1", null, spaceA.getId(), null, author.getId(), templateId);
        String firstContent = documentService.getDocumentContent(firstDocument);

        assertTrue(firstContent.contains("REQ-001"));
        assertTrue(firstContent.contains("REQ-002"));

        Document secondDocument = documentService.createDocument("Doc A2", null, spaceA.getId(), null, author.getId(), templateId);
        String secondContent = documentService.getDocumentContent(secondDocument);

        assertTrue(secondContent.contains("REQ-003"));
        assertTrue(secondContent.contains("REQ-004"));

        Space spaceB = spaceRepository.save(Space.create("Space B", "Second space", author.getId()));
        Document thirdDocument = documentService.createDocument("Doc B1", null, spaceB.getId(), null, author.getId(), templateId);
        String thirdContent = documentService.getDocumentContent(thirdDocument);

        assertTrue(thirdContent.contains("REQ-001"));
        assertTrue(thirdContent.contains("REQ-002"));
    }

    @Test
    void updateTemplateDocument_numbersOnlyNewRequirementRows() {
        User author = persistUser(uniqueLogin("editor"), "user123", "editor@kb.local", GlobalRole.EDITOR, false);
        createDocumentsTable();

        Long templateId = createRequirementTemplate();
        Space space = spaceRepository.save(Space.create("Save Numbering Space", "Space", author.getId()));
        Document document = documentService.createDocument("Save Numbering Doc", null, space.getId(), null, author.getId(), templateId);

        String updatedContent = """
            # Req

            | № | Name |
            |---|---|
            | REQ-001 | A |
            | REQ-002 | B |
            |   | C |
            | custom-id | D |
            | | E |
            """;

        Document updatedDocument = documentService.updateDocument(
                document.getId(),
                document.getTitle(),
                updatedContent,
                null,
                null,
                author.getId()
        );

        String savedContent = documentService.getDocumentContent(updatedDocument);
        assertTrue(savedContent.contains("| REQ-001 | A |"));
        assertTrue(savedContent.contains("| REQ-002 | B |"));
        assertTrue(savedContent.contains("| REQ-003 | C |"));
        assertTrue(savedContent.contains("| custom-id | D |"));
        assertTrue(savedContent.contains("| REQ-004 | E |"));
    }

    @Test
    void updateNonTemplateDocument_doesNotNumberRequirementRows() {
        User author = persistUser(uniqueLogin("editor"), "user123", "editor@kb.local", GlobalRole.EDITOR, false);
        createDocumentsTable();

        Space space = spaceRepository.save(Space.create("Plain Save Space", "Space", author.getId()));
        Document document = documentService.createDocument("Plain Save Doc", "# Plain", space.getId(), null, author.getId(), null);
        String updatedContent = """
            # Plain

            | № | Name |
            |---|---|
            |   | A |
            """;

        Document updatedDocument = documentService.updateDocument(
                document.getId(),
                document.getTitle(),
                updatedContent,
                null,
                null,
                author.getId()
        );

        assertEquals(updatedContent, documentService.getDocumentContent(updatedDocument));
    }

    @Test
    void updateDocumentApi_returnsActuallySavedNumberedContent() throws Exception {
        User author = persistUser(uniqueLogin("editor"), "user123", "editor@kb.local", GlobalRole.EDITOR, false);
        String jwt = loginAndGetJwt(author.getLogin(), "user123");
        createDocumentsTable();

        Long templateId = createRequirementTemplate();
        Space space = spaceRepository.save(Space.create("API Save Numbering Space", "Space", author.getId()));
        Document document = documentService.createDocument("API Save Numbering Doc", null, space.getId(), null, author.getId(), templateId);

        String updatedContent = """
            # Req

            | № | Name |
            |---|---|
            | REQ-001 | A |
            | REQ-002 | B |
            |   | C |
            """;

        String body = objectMapper.writeValueAsString(Map.of(
                "title", document.getTitle(),
                "content", updatedContent
        ));

        mockMvc.perform(put("/api/documents/" + document.getId())
                        .contentType(APPLICATION_JSON)
                        .content(body)
                        .cookie(jwtCookie(jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", containsString("| REQ-003 | C |")));

        assertTrue(documentService.getDocumentContent(document).contains("| REQ-003 | C |"));
    }

    private void createDocumentsTable() {
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

    private Long createRequirementTemplate() {
        String templateName = uniqueLogin("requirement-template");
        String templateContent = """
            # Req

            | № | Name |
            |---|---|
            |   | A |
            |   | B |
            """;

        jdbcTemplate.update(
                """
                INSERT INTO templates (name, description, content, role, is_system, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                templateName,
                "Template for requirement numbering tests",
                templateContent,
                "Analyst",
                true
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM templates WHERE name = ?",
                Long.class,
                templateName
        );
    }
}
