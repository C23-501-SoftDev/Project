package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository {
    Document save(Document document);
    Optional<Document> findById(Long id);
    List<Document> findBySpaceId(Long spaceId, boolean includeDeleted);
    List<Document> findBySpaceIdPaged(Long spaceId, boolean includeDeleted, int page, int size);
    long countBySpaceId(Long spaceId, boolean includeDeleted);
    List<Document> findBySpaceIdIn(List<Long> spaceIds, boolean includeDeleted);
    List<Document> findByAuthorId(Long authorId);
    boolean existsById(Long id);
    boolean existsByTitleAndSpaceIdAndParentId(String title, Long spaceId, Long parentId);
    Optional<Document> findBySpaceIdAndTitle(Long spaceId, String title);
    boolean existsByTitleAndSpaceIdAndNoParent(String title, Long spaceId);
    Page<Document> searchByTitle(String query, LocalDateTime effectiveFrom, LocalDateTime effectiveTo, Pageable pageable);
    Page<Document> searchByTitleInSpaces(Collection<Long> spaceIds, String query, LocalDateTime effectiveFrom, LocalDateTime effectiveTo, Pageable pageable);
    Page<Document> findBySpaceIdAndStatusPaged(Long spaceId, DocumentStatus status, Pageable pageable);
    long countBySpaceIdAndStatus(Long spaceId, DocumentStatus status);
    Page<Document> findByStatusPaged(DocumentStatus status, Pageable pageable);
    long countByStatus(DocumentStatus status);
    Page<Document> findBySpaceIdsAndStatusPaged(Collection<Long> spaceIds, DocumentStatus status, Pageable pageable);
    long countBySpaceIdsAndStatus(Collection<Long> spaceIds, DocumentStatus status);
    List<Long> findAncestorIds(Long documentId);
    List<Document> findAll(boolean includeDeleted);
    List<Document> findAccessibleByUserId(Long userId, boolean includeDeleted);
    void deleteById(Long id);
    boolean hasChildren(Long id);
}
