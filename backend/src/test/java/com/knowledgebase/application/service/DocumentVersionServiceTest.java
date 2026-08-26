package com.knowledgebase.application.service;

import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.DocumentStatus;
import com.knowledgebase.domain.model.DocumentVersion;
import com.knowledgebase.domain.model.DiffAlgorithmType;
import com.knowledgebase.domain.model.GitCommitResult;
import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.exception.DocumentVersionNotFoundException;
import com.knowledgebase.domain.repository.DocumentContentRepository;
import com.knowledgebase.domain.repository.DocumentRepository;
import com.knowledgebase.domain.repository.DocumentVersionRepository;
import com.knowledgebase.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class DocumentVersionServiceTest {

    @Test
    void restoresRegisteredVersionAsNewSnapshotWithCurrentDocumentPath() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentVersionRepository versions = mock(DocumentVersionRepository.class);
        DocumentContentRepository content = mock(DocumentContentRepository.class);
        UserRepository users = mock(UserRepository.class);
        AuditService audit = mock(AuditService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        Document document = Document.restore(7L, "Architecture", "spaces/current.md",
                DocumentStatus.PUBLISHED, null, 3L, 2L, null, null, null, LocalDateTime.now(), LocalDateTime.now());
        User editor = User.restore(9L, "editor", "hash", "editor@kb.local", GlobalRole.EDITOR,
                false, false, null, LocalDateTime.now(), LocalDateTime.now());
        String selectedHash = "a".repeat(40);
        String newHash = "b".repeat(40);
        LocalDateTime committedAt = LocalDateTime.of(2026, 8, 19, 12, 0);

        when(documents.findById(7L)).thenReturn(Optional.of(document));
        when(versions.findByDocumentIdAndGitHash(7L, selectedHash)).thenReturn(Optional.of(
                DocumentVersion.create(7L, selectedHash, "spaces/previous.md", 2L, "Old version", committedAt.minusDays(1))));
        when(users.findById(9L)).thenReturn(Optional.of(editor));
        when(content.readDocumentVersion("spaces/previous.md", selectedHash)).thenReturn(Optional.of("Restored text"));
        when(content.saveDocumentSnapshot(eq("spaces/current.md"), eq("spaces/current.md"), eq("Restored text"),
                eq(".metadata/documents/7.json"), any(), eq("Restore document version: " + selectedHash),
                eq("editor"), eq("editor@kb.local"))).thenReturn(new GitCommitResult(newHash, committedAt));
        when(documents.save(document)).thenReturn(document);
        when(versions.save(any(DocumentVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentVersion restored = service(documents, versions, content, users, events, audit)
                .restoreVersion(7L, selectedHash, 9L);

        assertEquals(newHash, restored.gitHash());
        assertEquals("spaces/current.md", restored.gitFilePath());
        assertEquals(9L, restored.authorId());
        assertEquals(committedAt, restored.createdAt());
        assertEquals("Restore document version: " + selectedHash, restored.comment());
    }

    @Test
    void rejectsMalformedRestoreHashBeforeReadingGit() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentContentRepository content = mock(DocumentContentRepository.class);

        assertThrows(IllegalArgumentException.class, () -> service(documents, mock(DocumentVersionRepository.class), content,
                mock(UserRepository.class), mock(ApplicationEventPublisher.class), mock(AuditService.class))
                .restoreVersion(7L, "not-a-git-hash", 9L));

        verifyNoInteractions(documents, content);
    }

    @Test
    void rejectsUnregisteredRestoreHashBeforeReadingGit() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentVersionRepository versions = mock(DocumentVersionRepository.class);
        DocumentContentRepository content = mock(DocumentContentRepository.class);
        String foreignHash = "c".repeat(40);
        Document document = Document.restore(7L, "Architecture", "spaces/current.md",
                DocumentStatus.PUBLISHED, null, 3L, 2L, null, null, null, LocalDateTime.now(), LocalDateTime.now());
        when(documents.findById(7L)).thenReturn(Optional.of(document));
        when(versions.findByDocumentIdAndGitHash(7L, foreignHash)).thenReturn(Optional.empty());

        assertThrows(DocumentVersionNotFoundException.class, () -> service(documents, versions, content,
                mock(UserRepository.class), mock(ApplicationEventPublisher.class), mock(AuditService.class))
                .restoreVersion(7L, foreignHash, 9L));

        verifyNoInteractions(content);
    }

    @Test
    void leavesDatabaseUntouchedWhenGitReadFailsDuringRestore() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentVersionRepository versions = mock(DocumentVersionRepository.class);
        DocumentContentRepository content = mock(DocumentContentRepository.class);
        UserRepository users = mock(UserRepository.class);
        String selectedHash = "d".repeat(40);
        Document document = Document.restore(7L, "Architecture", "spaces/current.md",
                DocumentStatus.PUBLISHED, null, 3L, 2L, null, null, null, LocalDateTime.now(), LocalDateTime.now());
        when(documents.findById(7L)).thenReturn(Optional.of(document));
        when(versions.findByDocumentIdAndGitHash(7L, selectedHash)).thenReturn(Optional.of(
                DocumentVersion.create(7L, selectedHash, "spaces/current.md", 2L, "Old version", LocalDateTime.now())));
        when(users.findById(9L)).thenReturn(Optional.of(User.restore(9L, "editor", "hash", "editor@kb.local",
                GlobalRole.EDITOR, false, false, null, LocalDateTime.now(), LocalDateTime.now())));
        when(content.readDocumentVersion("spaces/current.md", selectedHash))
                .thenThrow(new RuntimeException("Git read failed"));

        assertThrows(RuntimeException.class, () -> service(documents, versions, content, users,
                mock(ApplicationEventPublisher.class), mock(AuditService.class)).restoreVersion(7L, selectedHash, 9L));

        verify(documents, never()).save(any(Document.class));
        verify(versions, never()).save(any(DocumentVersion.class));
    }

    @Test
    void surfacesPersistenceFailureAfterCreatingGitSnapshot() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentVersionRepository versions = mock(DocumentVersionRepository.class);
        DocumentContentRepository content = mock(DocumentContentRepository.class);
        UserRepository users = mock(UserRepository.class);
        String selectedHash = "e".repeat(40);
        String newHash = "f".repeat(40);
        Document document = Document.restore(7L, "Architecture", "spaces/current.md",
                DocumentStatus.PUBLISHED, null, 3L, 2L, null, null, null, LocalDateTime.now(), LocalDateTime.now());
        User editor = User.restore(9L, "editor", "hash", "editor@kb.local", GlobalRole.EDITOR,
                false, false, null, LocalDateTime.now(), LocalDateTime.now());
        when(documents.findById(7L)).thenReturn(Optional.of(document));
        when(versions.findByDocumentIdAndGitHash(7L, selectedHash)).thenReturn(Optional.of(
                DocumentVersion.create(7L, selectedHash, "spaces/current.md", 2L, "Old version", LocalDateTime.now())));
        when(users.findById(9L)).thenReturn(Optional.of(editor));
        when(content.readDocumentVersion("spaces/current.md", selectedHash)).thenReturn(Optional.of("Restored text"));
        when(content.saveDocumentSnapshot(eq("spaces/current.md"), eq("spaces/current.md"), eq("Restored text"),
                eq(".metadata/documents/7.json"), any(), eq("Restore document version: " + selectedHash),
                eq("editor"), eq("editor@kb.local"))).thenReturn(new GitCommitResult(newHash, LocalDateTime.now()));
        when(documents.save(document)).thenReturn(document);
        when(versions.save(any(DocumentVersion.class))).thenThrow(new RuntimeException("DB write failed"));

        assertThrows(RuntimeException.class, () -> service(documents, versions, content, users,
                mock(ApplicationEventPublisher.class), mock(AuditService.class)).restoreVersion(7L, selectedHash, 9L));

        verify(content).saveDocumentSnapshot(eq("spaces/current.md"), eq("spaces/current.md"), eq("Restored text"),
                eq(".metadata/documents/7.json"), any(), eq("Restore document version: " + selectedHash),
                eq("editor"), eq("editor@kb.local"));
    }

    @Test
    void rejectsVersionHashThatIsNotRegisteredForDocumentBeforeReadingGit() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentVersionRepository versions = mock(DocumentVersionRepository.class);
        DocumentContentRepository content = mock(DocumentContentRepository.class);
        Document document = Document.restore(7L, "Architecture", "spaces/main/architecture.md",
                DocumentStatus.PUBLISHED, null, 3L, 2L, null, null, null, LocalDateTime.now(), LocalDateTime.now());
        String registeredHash = "a".repeat(40);
        String foreignHash = "b".repeat(40);

        when(documents.findById(7L)).thenReturn(Optional.of(document));
        when(versions.findByDocumentIdAndGitHash(7L, registeredHash))
                .thenReturn(Optional.of(DocumentVersion.create(7L, registeredHash, "spaces/test.md", 2L, null, LocalDateTime.now())));
        when(versions.findByDocumentIdAndGitHash(7L, foreignHash)).thenReturn(Optional.empty());

        DocumentVersionService service = service(documents, versions, content, mock(UserRepository.class),
                mock(ApplicationEventPublisher.class), mock(AuditService.class));

        assertThrows(DocumentVersionNotFoundException.class,
                () -> service.compareVersions(7L, registeredHash, foreignHash));

        verifyNoInteractions(content);
    }

    @Test
    void usesCurrentDocumentPathForLegacyVersionWithoutHistoricalPath() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentVersionRepository versions = mock(DocumentVersionRepository.class);
        DocumentContentRepository content = mock(DocumentContentRepository.class);
        Document document = Document.restore(7L, "Architecture", "spaces/current.md",
                DocumentStatus.PUBLISHED, null, 3L, 2L, null, null, null, LocalDateTime.now(), LocalDateTime.now());
        String fromHash = "a".repeat(40);
        String toHash = "b".repeat(40);

        when(documents.findById(7L)).thenReturn(Optional.of(document));
        when(versions.findByDocumentIdAndGitHash(7L, fromHash))
                .thenReturn(Optional.of(DocumentVersion.create(7L, fromHash, null, 2L, null, LocalDateTime.now())));
        when(versions.findByDocumentIdAndGitHash(7L, toHash))
                .thenReturn(Optional.of(DocumentVersion.create(7L, toHash, "spaces/test.md", 2L, null, LocalDateTime.now())));
        when(content.diffDocumentVersions("spaces/current.md", "spaces/test.md", fromHash, toHash,
                2000, 1_048_576, false, DiffAlgorithmType.HYBRID)).thenReturn(List.of());

        DocumentVersionService service = service(documents, versions, content, mock(UserRepository.class),
                mock(ApplicationEventPublisher.class), mock(AuditService.class));

        service.compareVersions(7L, fromHash, toHash);

        verify(content).diffDocumentVersions("spaces/current.md", "spaces/test.md", fromHash, toHash,
                2000, 1_048_576, false, DiffAlgorithmType.HYBRID);
    }

    private DocumentVersionService service(DocumentRepository documents, DocumentVersionRepository versions,
                                           DocumentContentRepository content, UserRepository users,
                                           ApplicationEventPublisher events, AuditService audit) {
        return new DocumentVersionService(documents, versions, content, users, events, audit, 2000, 1_048_576);
    }
}
