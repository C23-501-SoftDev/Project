package com.knowledgebase.application.service;

import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.DocumentStatus;
import com.knowledgebase.domain.model.GitCommitResult;
import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.DocumentContentRepository;
import com.knowledgebase.domain.repository.DocumentRepository;
import com.knowledgebase.domain.repository.DocumentVersionRepository;
import com.knowledgebase.domain.repository.SpacePermissionRepository;
import com.knowledgebase.domain.repository.SpaceRepository;
import com.knowledgebase.domain.repository.TemplateRepository;
import com.knowledgebase.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceVersioningTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentContentRepository contentRepository;
    @Mock private DocumentVersionRepository documentVersionRepository;
    @Mock private SpaceRepository spaceRepository;
    @Mock private SpacePermissionRepository permissionRepository;
    @Mock private TemplateRepository templateRepository;
    @Mock private UserRepository userRepository;
    @Mock private RequirementNumberService requirementNumberService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AuditService auditService;

    private DocumentService service;
    private Document document;

    @BeforeEach
    void setUp() {
        service = new DocumentService(documentRepository, contentRepository, documentVersionRepository,
                spaceRepository, permissionRepository, templateRepository, userRepository,
                requirementNumberService, eventPublisher, auditService);
        document = Document.restore(10L, "Document", "spaces/Space/Document.md", DocumentStatus.DRAFT,
                1L, 2L, null, null, LocalDateTime.now(), LocalDateTime.now());

        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        when(userRepository.findById(3L)).thenReturn(Optional.of(User.restore(3L, "editor", "hash",
                "editor@kb.local", GlobalRole.EDITOR, false, false, null, LocalDateTime.now(), LocalDateTime.now())));
        when(spaceRepository.findById(2L)).thenReturn(Optional.of(Space.restore(2L, "Space", "", 1L,
                false, LocalDateTime.now(), LocalDateTime.now())));
        when(contentRepository.findContentByPath(document.getGitFilePath())).thenReturn(Optional.of("# Before"));
    }

    @Test
    void gitFailureDoesNotPersistDocumentOrVersion() {
        when(contentRepository.saveDocumentSnapshot(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Git unavailable"));

        assertThrows(RuntimeException.class, () -> service.updateDocument(10L, null, "# After",
                DocumentStatus.PUBLISHED, null, 3L));

        verify(documentRepository, never()).save(any());
        verify(documentVersionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher, auditService);
    }

    @Test
    void persistenceFailureAfterCommitLogsAndPropagatesWithoutPublishingEvent() {
        when(contentRepository.saveDocumentSnapshot(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new GitCommitResult("a".repeat(40), LocalDateTime.now()));
        when(documentRepository.save(any())).thenReturn(document);
        doThrow(new RuntimeException("Database unavailable")).when(documentVersionRepository).save(any());

        assertThrows(RuntimeException.class, () -> service.updateDocument(10L, null, "# After",
                DocumentStatus.PUBLISHED, null, 3L));

        verify(documentRepository).save(document);
        verify(documentVersionRepository).save(any());
        verifyNoInteractions(eventPublisher, auditService);
    }
}
