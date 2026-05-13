package com.knowledgebase.integration;

import com.knowledgebase.infrastructure.repository.git.JGitDocumentContentRepository;
import com.knowledgebase.support.IntegrationTestBase;
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
        gitRepository.saveContent(path, content, message, "Test Author", "test@knowledgebase.local");
        
        // Then
        Optional<String> readContent = gitRepository.findContentByPath(path);
        assertTrue(readContent.isPresent(), "Content should be present");
        assertEquals(content, readContent.get());
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
