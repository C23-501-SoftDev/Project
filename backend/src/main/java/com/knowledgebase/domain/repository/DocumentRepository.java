package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.Document;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository {
    Document save(Document document);
    Optional<Document> findById(Long id);
    List<Document> findBySpaceId(Long spaceId, boolean includeDeleted);
    List<Document> findByAuthorId(Long authorId);
    boolean existsById(Long id);
    boolean existsByTitleAndSpaceIdAndParentId(String title, Long spaceId, Long parentId);
    boolean existsByTitleAndSpaceIdAndNoParent(String title, Long spaceId);
    List<Long> findAncestorIds(Long documentId);
    void deleteById(Long id);
}
