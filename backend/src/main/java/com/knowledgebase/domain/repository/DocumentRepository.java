package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.Document;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository {
    Document save(Document document);
    Optional<Document> findById(Long id);
    List<Document> findBySpaceId(Long spaceId, boolean includeDeleted);
    List<Document> findBySpaceIdPaged(Long spaceId, boolean includeDeleted, int page, int size);
    long countBySpaceId(Long spaceId, boolean includeDeleted);
    List<Document> findBySpaceIdAndAuthorIdPaged(Long spaceId, Long authorId, boolean includeDeleted, int page, int size);
    long countBySpaceIdAndAuthorId(Long spaceId, Long authorId, boolean includeDeleted);
    List<Document> findBySpaceIdIn(List<Long> spaceIds, boolean includeDeleted);
    List<Document> findByAuthorId(Long authorId);
    boolean existsById(Long id);
    boolean existsByTitleAndSpaceIdAndParentId(String title, Long spaceId, Long parentId);
    Optional<Document> findBySpaceIdAndTitle(Long spaceId, String title);
    boolean existsByTitleAndSpaceIdAndNoParent(String title, Long spaceId);
    List<com.knowledgebase.domain.model.User> findDistinctAuthorsByAccessibleSpaces(Long userId);
    List<Long> findAncestorIds(Long documentId);
    List<Document> findAll(boolean includeDeleted);
    List<Document> findAccessibleByUserId(Long userId, boolean includeDeleted);
    void deleteById(Long id);
    boolean hasChildren(Long id);
    void flush();
}
