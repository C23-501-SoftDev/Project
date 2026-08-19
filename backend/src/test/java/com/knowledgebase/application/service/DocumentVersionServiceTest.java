package com.knowledgebase.application.service;

import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.DocumentStatus;
import com.knowledgebase.domain.model.DocumentVersion;
import com.knowledgebase.domain.exception.DocumentVersionNotFoundException;
import com.knowledgebase.domain.repository.DocumentContentRepository;
import com.knowledgebase.domain.repository.DocumentRepository;
import com.knowledgebase.domain.repository.DocumentVersionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentVersionServiceTest {

    @Test
    void rejectsVersionHashThatIsNotRegisteredForDocumentBeforeReadingGit() {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentVersionRepository versions = mock(DocumentVersionRepository.class);
        DocumentContentRepository content = mock(DocumentContentRepository.class);
        Document document = Document.restore(7L, "Architecture", "spaces/main/architecture.md",
                DocumentStatus.PUBLISHED, 3L, 2L, null, null, LocalDateTime.now(), LocalDateTime.now());
        String registeredHash = "a".repeat(40);
        String foreignHash = "b".repeat(40);

        when(documents.findById(7L)).thenReturn(Optional.of(document));
        when(versions.findByDocumentIdAndGitHash(7L, registeredHash))
                .thenReturn(Optional.of(DocumentVersion.create(7L, registeredHash, "spaces/test.md", 2L, null, LocalDateTime.now())));
        when(versions.findByDocumentIdAndGitHash(7L, foreignHash)).thenReturn(Optional.empty());

        DocumentVersionService service = new DocumentVersionService(documents, versions, content, 2000, 1_048_576);

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
                DocumentStatus.PUBLISHED, 3L, 2L, null, null, LocalDateTime.now(), LocalDateTime.now());
        String fromHash = "a".repeat(40);
        String toHash = "b".repeat(40);

        when(documents.findById(7L)).thenReturn(Optional.of(document));
        when(versions.findByDocumentIdAndGitHash(7L, fromHash))
                .thenReturn(Optional.of(DocumentVersion.create(7L, fromHash, null, 2L, null, LocalDateTime.now())));
        when(versions.findByDocumentIdAndGitHash(7L, toHash))
                .thenReturn(Optional.of(DocumentVersion.create(7L, toHash, "spaces/test.md", 2L, null, LocalDateTime.now())));
        when(content.diffDocumentVersions("spaces/current.md", "spaces/test.md", fromHash, toHash,
                2000, 1_048_576, false)).thenReturn(List.of());

        DocumentVersionService service = new DocumentVersionService(documents, versions, content, 2000, 1_048_576);

        service.compareVersions(7L, fromHash, toHash);

        verify(content).diffDocumentVersions("spaces/current.md", "spaces/test.md", fromHash, toHash,
                2000, 1_048_576, false);
    }
}
