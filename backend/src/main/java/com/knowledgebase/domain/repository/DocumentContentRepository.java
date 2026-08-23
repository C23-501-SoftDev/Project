package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.GitCommitResult;
import com.knowledgebase.domain.model.DiffLine;
import com.knowledgebase.domain.model.DiffAlgorithmType;

import java.util.List;
import java.util.Optional;

/**
 * Интерфейс хранилища содержимого документов (Git).
 */
public interface DocumentContentRepository {

    /**
     * Сохраняет содержимое документа в Git.
     * @param gitFilePath путь к файлу в репозитории
     * @param content содержимое в формате Markdown
     * @param commitMessage сообщение коммита
     * @param authorName имя автора для коммита
     * @param authorEmail email автора для коммита
     */
    GitCommitResult saveContent(String gitFilePath, String content, String commitMessage, String authorName, String authorEmail);

    /**
     * Saves a document and a versioned metadata sidecar in one Git commit. If the
     * document was renamed, the old path is removed in that same commit.
     */
    GitCommitResult saveDocumentSnapshot(String oldGitFilePath, String gitFilePath, String content,
                                         String metadataPath, String metadataContent, String commitMessage,
                                         String authorName, String authorEmail);

    /**
     * Читает содержимое документа из Git.
     * @param gitFilePath путь к файлу
     * @return содержимое или empty, если файл не найден
     */
    Optional<String> findContentByPath(String gitFilePath);

    /**
     * Reads a document file from an immutable Git commit without changing the
     * repository working tree or HEAD.
     *
     * @param gitFilePath repository-relative document path
     * @param gitHash full 40-character Git commit SHA
     * @return file content, or empty when the file is absent from that commit
     */
    Optional<String> readDocumentVersion(String gitFilePath, String gitHash);

    List<DiffLine> diffDocumentVersions(String fromPath, String toPath, String fromHash, String toHash,
                                        int maxLines, int maxBytes);

    default List<DiffLine> diffDocumentVersions(String fromPath, String toPath, String fromHash, String toHash,
                                                int maxLines, int maxBytes, boolean includeAllContext) {
        return diffDocumentVersions(fromPath, toPath, fromHash, toHash, maxLines, maxBytes);
    }

    default List<DiffLine> diffDocumentVersions(String fromPath, String toPath, String fromHash, String toHash,
                                                int maxLines, int maxBytes, boolean includeAllContext,
                                                DiffAlgorithmType algorithm) {
        return diffDocumentVersions(fromPath, toPath, fromHash, toHash, maxLines, maxBytes, includeAllContext);
    }

    /**
     * Перемещает файл в Git (используется при архивации).
     * @param oldPath текущий путь
     * @param newPath новый путь
     * @param commitMessage сообщение коммита
     */
    void moveContent(String oldPath, String newPath, String commitMessage);

    /**
     * Удаляет файл из Git.
     */
    void deleteContent(String gitFilePath, String commitMessage);
}
