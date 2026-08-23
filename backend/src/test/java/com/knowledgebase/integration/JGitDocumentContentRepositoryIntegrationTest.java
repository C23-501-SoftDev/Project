package com.knowledgebase.integration;

import com.knowledgebase.infrastructure.repository.git.JGitDocumentContentRepository;
import com.knowledgebase.support.IntegrationTestBase;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест для JGitDocumentContentRepository.
 * Проверяет взаимодействие с файловой системой и JGit.
 */
class JGitDocumentContentRepositoryIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JGitDocumentContentRepository gitRepository;

    @Test
    void shouldSaveAndReadContent() {
        // Given
        String path = "test/doc1.md";
        String content = "# Hello World\nThis is a test document.";
        String message = "Create test document";
        
        // When
        var commit = gitRepository.saveContent(path, content, message, "Test Author", "test@knowledgebase.local");
        
        // Then
        Optional<String> readContent = gitRepository.findContentByPath(path);
        assertTrue(readContent.isPresent(), "Content should be present");
        assertEquals(content, readContent.get());
        assertNotNull(commit);
        assertEquals(40, commit.hash().length());
    }

    @Test
    void shouldCommitSnapshotWithAuthorAndMetadataInOneCommit() throws Exception {
        var commit = gitRepository.saveDocumentSnapshot(
                "docs/current.md", "docs/current.md", "# Current", ".metadata/documents/42.json",
                "{\"documentId\":42}", "Update document: Current", "Editor", "editor@kb.local");

        try (Git git = Git.open(tempDir.resolve("git-repo").toFile())) {
            var head = git.log().setMaxCount(1).call().iterator().next();
            assertEquals(commit.hash(), head.getId().name());
            assertEquals("Editor", head.getAuthorIdent().getName());
            assertEquals("editor@kb.local", head.getAuthorIdent().getEmailAddress());
        }
        assertEquals("# Current", gitRepository.findContentByPath("docs/current.md").orElseThrow());
        assertEquals("{\"documentId\":42}",
                gitRepository.findContentByPath(".metadata/documents/42.json").orElseThrow());
    }

    @Test
    void readsHistoricalBlobWithoutMovingHead() throws Exception {
        var firstCommit = gitRepository.saveContent("docs/versioned.md", "First version",
                "First version", "Editor", "editor@kb.local");
        var secondCommit = gitRepository.saveContent("docs/versioned.md", "Second version",
                "Second version", "Editor", "editor@kb.local");

        assertEquals("First version", gitRepository.readDocumentVersion("docs/versioned.md", firstCommit.hash()).orElseThrow());

        try (Git git = Git.open(tempDir.resolve("git-repo").toFile())) {
            assertEquals(secondCommit.hash(), git.log().setMaxCount(1).call().iterator().next().getId().name());
        }
    }

    @Test
    void shouldMoveContent() {
        // Given
        String oldPath = "old/folder/doc.md";
        String newPath = "archive/doc.md";
        String content = "Moving content test";
        
        gitRepository.saveContent(oldPath, content, "Initial save", "Author", "author@test.local");
        
        // When
        gitRepository.moveContent(oldPath, newPath, "Move to archive");
        
        // Then
        assertFalse(gitRepository.findContentByPath(oldPath).isPresent(), "Old file should be gone");
        Optional<String> readContent = gitRepository.findContentByPath(newPath);
        assertTrue(readContent.isPresent(), "New file should exist");
        assertEquals(content, readContent.get());
    }

    @Test
    void shouldDeleteContent() {
        // Given
        String path = "temp/delete-me.md";
        gitRepository.saveContent(path, "Temporary content", "Initial save", "Author", "author@test.local");
        assertTrue(gitRepository.findContentByPath(path).isPresent());
        
        // When
        gitRepository.deleteContent(path, "Delete document");
        
        // Then
        assertFalse(gitRepository.findContentByPath(path).isPresent(), "File should be deleted");
    }
}
