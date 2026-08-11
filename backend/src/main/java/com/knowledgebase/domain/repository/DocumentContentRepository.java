package com.knowledgebase.domain.repository;

import java.util.List;
import java.util.Optional;

/**
 * Интерфейс хранилища содержимого документов (Git).
 */
public interface DocumentContentRepository {

    /**
     * Информация о коммите (версии документа).
     */
    record CommitLogEntry(
        String commitId,
        String authorName,
        String authorEmail,
        String commitMessage,
        java.time.LocalDateTime timestamp
    ) {}
    /**
     * Сохраняет содержимое документа в Git.
     * @param gitFilePath путь к файлу в репозитории
     * @param content содержимое в формате Markdown
     * @param commitMessage сообщение коммита
     * @param authorName имя автора для коммита
     * @param authorEmail email автора для коммита
     */
    void saveContent(String gitFilePath, String content, String commitMessage, String authorName, String authorEmail);

    /**
     * Читает содержимое документа из Git.
     * @param gitFilePath путь к файлу
     * @return содержимое или empty, если файл не найден
     */
    Optional<String> findContentByPath(String gitFilePath);

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

    /**
     * Возвращает историю коммитов для файла в Git.
     * @param gitFilePath путь к файлу в репозитории
     * @return список коммитов
     */
    List<CommitLogEntry> getHistory(String gitFilePath);
}

