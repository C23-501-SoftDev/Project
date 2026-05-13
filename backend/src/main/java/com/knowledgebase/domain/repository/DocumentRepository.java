package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.DocumentStatus;

import java.util.List;
import java.util.Optional;

/**
 * Интерфейс репозитория документов (Domain Layer).
 */
public interface DocumentRepository {

    /**
     * Сохраняет метаданные документа в БД.
     * @param document документ для сохранения
     * @return сохранённый документ с ID
     */
    Document save(Document document);

    /**
     * Находит документ по ID.
     */
    Optional<Document> findById(Long id);

    /**
     * Возвращает список документов в пространстве.
     * @param spaceId ID пространства
     * @param includeDeleted включать ли удаленные документы
     */
    List<Document> findBySpaceId(Long spaceId, boolean includeDeleted);

    /**
     * Возвращает список документов автора.
     */
    List<Document> findByAuthorId(Long authorId);

    /**
     * Проверяет существование документа.
     */
    boolean existsById(Long id);

    /**
     * Удаляет документ из БД (hard delete).
     * Обычно используется только в тестах или админами.
     */
    void deleteById(Long id);
}
