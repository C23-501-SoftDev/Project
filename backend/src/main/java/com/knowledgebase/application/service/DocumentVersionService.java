package com.knowledgebase.application.service;

import com.knowledgebase.domain.exception.DocumentNotFoundException;
import com.knowledgebase.domain.exception.DocumentVersionNotFoundException;
import com.knowledgebase.domain.exception.DocumentVersionPathUnavailableException;
import com.knowledgebase.domain.model.DocumentDiff;
import com.knowledgebase.domain.model.DiffAlgorithmType;
import com.knowledgebase.domain.repository.DocumentContentRepository;
import com.knowledgebase.domain.repository.DocumentRepository;
import com.knowledgebase.domain.repository.DocumentVersionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;
import java.util.Locale;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DocumentVersionService {
    private static final Pattern GIT_HASH = Pattern.compile("^[0-9a-fA-F]{40}$");

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentContentRepository contentRepository;
    private final int maxDiffLines;
    private final int maxDiffBytes;

    public DocumentVersionService(DocumentRepository documentRepository,
                                  DocumentVersionRepository versionRepository,
                                  DocumentContentRepository contentRepository,
                                  @Value("${app.document-version.diff-max-lines:2000}") int maxDiffLines,
                                  @Value("${app.document-version.diff-max-bytes:1048576}") int maxDiffBytes) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.contentRepository = contentRepository;
        this.maxDiffLines = maxDiffLines;
        this.maxDiffBytes = maxDiffBytes;
    }

    public DocumentDiff compareVersions(Long documentId, String fromHash, String toHash) {
        return compareVersions(documentId, fromHash, toHash, false);
    }

    public DocumentDiff compareVersions(Long documentId, String fromHash, String toHash, boolean includeAllContext) {
        return compareVersions(documentId, fromHash, toHash, includeAllContext, DiffAlgorithmType.HYBRID);
    }

    public DocumentDiff compareVersions(Long documentId, String fromHash, String toHash, boolean includeAllContext,
                                        DiffAlgorithmType algorithm) {
        validateHashes(fromHash, toHash);
        fromHash = fromHash.toLowerCase(Locale.ROOT);
        toHash = toHash.toLowerCase(Locale.ROOT);
        var document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        var fromVersion = requireRegisteredVersion(documentId, fromHash);
        var toVersion = requireRegisteredVersion(documentId, toHash);
        return new DocumentDiff(documentId, fromHash, toHash, algorithm,
                contentRepository.diffDocumentVersions(
                        pathForVersion(fromVersion, document.getGitFilePath()),
                        pathForVersion(toVersion, document.getGitFilePath()),
                        fromHash, toHash, maxDiffLines, maxDiffBytes, includeAllContext, algorithm));
    }

    public List<com.knowledgebase.domain.model.DocumentVersion> listVersions(Long documentId) {
        documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        return versionRepository.findByDocumentIdOrderByCreatedAtAsc(documentId);
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
}
