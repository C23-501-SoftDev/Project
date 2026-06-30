package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository {
    Document save(Document document);
    Optional<Document> findById(Long id);
    List<Document> findBySpaceId(Long spaceId, boolean includeDeleted);
    List<Document> findByAuthorId(Long authorId);
    boolean existsById(Long id);
    boolean existsByTitleAndSpaceIdAndParentId(String title, Long spaceId, Long parentId);
    Optional<Document> findBySpaceIdAndTitle(Long spaceId, String title);
    boolean existsByTitleAndSpaceIdAndNoParent(String title, Long spaceId);
    Page<Document> searchByTitle(String query, Pageable pageable);
    Page<Document> searchByTitleInSpaces(Collection<Long> spaceIds, String query, Pageable pageable);
    List<Long> findAncestorIds(Long documentId);
    List<Document> findAll(boolean includeDeleted);
    List<Document> findAccessibleByUserId(Long userId, boolean includeDeleted);
    void deleteById(Long id);
    boolean hasChildren(Long id);
}
