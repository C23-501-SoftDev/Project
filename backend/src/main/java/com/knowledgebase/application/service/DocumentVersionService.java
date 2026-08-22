package com.knowledgebase.application.service;

import com.knowledgebase.domain.event.DocumentUpdatedEvent;
import com.knowledgebase.domain.exception.DocumentNotFoundException;
import com.knowledgebase.domain.exception.DocumentVersionNotFoundException;
import com.knowledgebase.domain.exception.DocumentVersionPathUnavailableException;
import com.knowledgebase.domain.exception.UserNotFoundException;
import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.DocumentDiff;
import com.knowledgebase.domain.model.DocumentStatus;
import com.knowledgebase.domain.model.DocumentVersion;
import com.knowledgebase.domain.model.GitCommitResult;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.DocumentContentRepository;
import com.knowledgebase.domain.repository.DocumentRepository;
import com.knowledgebase.domain.repository.DocumentVersionRepository;
import com.knowledgebase.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;
import java.util.Locale;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DocumentVersionService {
    private static final Pattern GIT_HASH = Pattern.compile("^[0-9a-fA-F]{40}$");
    private static final Logger log = LoggerFactory.getLogger(DocumentVersionService.class);

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentContentRepository contentRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final int maxDiffLines;
    private final int maxDiffBytes;

    public DocumentVersionService(DocumentRepository documentRepository,
                                  DocumentVersionRepository versionRepository,
                                  DocumentContentRepository contentRepository,
                                  UserRepository userRepository,
                                  ApplicationEventPublisher eventPublisher,
                                  AuditService auditService,
                                  @Value("${app.document-version.diff-max-lines:2000}") int maxDiffLines,
                                  @Value("${app.document-version.diff-max-bytes:1048576}") int maxDiffBytes) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.contentRepository = contentRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.maxDiffLines = maxDiffLines;
        this.maxDiffBytes = maxDiffBytes;
    }

    public DocumentDiff compareVersions(Long documentId, String fromHash, String toHash) {
        return compareVersions(documentId, fromHash, toHash, false);
    }

    public DocumentDiff compareVersions(Long documentId, String fromHash, String toHash, boolean includeAllContext) {
        validateHashes(fromHash, toHash);
        fromHash = fromHash.toLowerCase(Locale.ROOT);
        toHash = toHash.toLowerCase(Locale.ROOT);
        var document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        var fromVersion = requireRegisteredVersion(documentId, fromHash);
        var toVersion = requireRegisteredVersion(documentId, toHash);
        return new DocumentDiff(documentId, fromHash, toHash,
                contentRepository.diffDocumentVersions(
                        pathForVersion(fromVersion, document.getGitFilePath()),
                        pathForVersion(toVersion, document.getGitFilePath()),
                        fromHash, toHash, maxDiffLines, maxDiffBytes, includeAllContext));
    }

    public List<com.knowledgebase.domain.model.DocumentVersion> listVersions(Long documentId) {
        documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        return versionRepository.findByDocumentIdOrderByCreatedAtAsc(documentId);
    }

    @Transactional
    public DocumentVersion restoreVersion(Long documentId, String gitHash, Long editorId) {
        validateHash(gitHash);
        String normalizedHash = gitHash.toLowerCase(Locale.ROOT);
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        if (document.getStatus() != DocumentStatus.PUBLISHED) {
            throw new IllegalArgumentException("Восстановление доступно только для опубликованного документа");
        }
        DocumentVersion selectedVersion = requireRegisteredVersion(documentId, normalizedHash);
        User editor = userRepository.findById(editorId)
                .orElseThrow(() -> new UserNotFoundException(editorId));

        String restoredContent = contentRepository.readDocumentVersion(
                        pathForVersion(selectedVersion, document.getGitFilePath()), normalizedHash)
                .orElseThrow(() -> new IllegalStateException("Файл документа отсутствует в выбранной Git-версии"));
        String commitMessage = "Restore document version: " + normalizedHash;
        GitCommitResult commit = contentRepository.saveDocumentSnapshot(
                document.getGitFilePath(), document.getGitFilePath(), restoredContent,
                ".metadata/documents/" + document.getId() + ".json", documentMetadataJson(document),
                commitMessage, editor.getLogin(), editor.getEmail());
        if (commit == null) {
            throw new IllegalStateException("Восстановленный документ не был добавлен в Git-коммит");
        }

        try {
            document.updateMetadata(null, document.getStatus());
            documentRepository.save(document);
            DocumentVersion restoredVersion = versionRepository.save(DocumentVersion.create(
                    document.getId(), commit.hash(), document.getGitFilePath(), editor.getId(),
                    commitMessage, commit.committedAt()));
            auditService.record("DOCUMENT_VERSION_RESTORED", AuditService.RESOURCE_DOCUMENT, document.getId(),
                    "restoredFrom=" + normalizedHash + ", restoredTo=" + commit.hash());
            eventPublisher.publishEvent(new DocumentUpdatedEvent(document.getId(), document.getTitle(),
                    document.getSpaceId(), editor.getId(), editor.getLogin()));
            return restoredVersion;
        } catch (RuntimeException exception) {
            log.error("Git-коммит {} восстановления документа {} не прикреплён к БД из-за ошибки persistence",
                    commit.hash(), documentId, exception);
            throw exception;
        }
    }

    private com.knowledgebase.domain.model.DocumentVersion requireRegisteredVersion(Long documentId, String gitHash) {
        return versionRepository.findByDocumentIdAndGitHash(documentId, gitHash)
                .orElseThrow(() -> new DocumentVersionNotFoundException(documentId, gitHash));
    }

    private String pathForVersion(com.knowledgebase.domain.model.DocumentVersion version, String currentDocumentPath) {
        if (version.gitFilePath() != null && !version.gitFilePath().isBlank()) {
            return version.gitFilePath();
        }
        if (currentDocumentPath == null || currentDocumentPath.isBlank()) {
            throw new DocumentVersionPathUnavailableException(version.documentId(), version.gitHash());
        }
        return currentDocumentPath;
    }

    private void validateHashes(String fromHash, String toHash) {
        if (fromHash == null || toHash == null || !GIT_HASH.matcher(fromHash).matches()
                || !GIT_HASH.matcher(toHash).matches()) {
            throw new IllegalArgumentException("Параметры from и to должны быть 40-символьными SHA Git-коммита");
        }
        if (fromHash.equalsIgnoreCase(toHash)) {
            throw new IllegalArgumentException("Для сравнения нужно выбрать две разные версии");
        }
    }

    private void validateHash(String gitHash) {
        if (gitHash == null || !GIT_HASH.matcher(gitHash).matches()) {
            throw new IllegalArgumentException("Параметр gitHash должен быть 40-символьным SHA Git-коммита");
        }
    }

    private String documentMetadataJson(Document document) {
        return "{\n"
                + "  \"documentId\": " + document.getId() + ",\n"
                + "  \"title\": \"" + jsonEscape(document.getTitle()) + "\",\n"
                + "  \"status\": \"" + document.getStatus().name() + "\",\n"
                + "  \"parentDocumentId\": "
                + (document.getParentDocumentId() == null ? "null" : document.getParentDocumentId()) + "\n"
                + "}\n";
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
