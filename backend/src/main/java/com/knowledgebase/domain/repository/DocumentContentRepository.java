package com.knowledgebase.domain.repository;

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
}
