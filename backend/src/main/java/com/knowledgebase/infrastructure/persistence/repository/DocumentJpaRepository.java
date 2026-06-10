package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.infrastructure.persistence.entity.DocumentJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface DocumentJpaRepository extends JpaRepository<DocumentJpaEntity, Long> {

    List<DocumentJpaEntity> findBySpaceId(Long spaceId);

    List<DocumentJpaEntity> findBySpaceIdAndStatusNot(Long spaceId, String status);

    Page<DocumentJpaEntity> findBySpaceIdAndStatusNot(Long spaceId, String status, Pageable pageable);

    Page<DocumentJpaEntity> findBySpaceId(Long spaceId, Pageable pageable);

    Page<DocumentJpaEntity> findBySpaceIdAndStatus(Long spaceId, String status, Pageable pageable);

    long countBySpaceIdAndStatus(Long spaceId, String status);

    long countBySpaceIdAndStatusNot(Long spaceId, String status);

    long countBySpaceId(Long spaceId);

    Page<DocumentJpaEntity> findByStatus(String status, Pageable pageable);

    long countByStatus(String status);

    Page<DocumentJpaEntity> findBySpaceIdInAndStatus(Collection<Long> spaceIds, String status, Pageable pageable);

    long countBySpaceIdInAndStatus(Collection<Long> spaceIds, String status);
    Page<DocumentJpaEntity> findBySpaceIdAndAuthorIdAndStatusNot(Long spaceId, Long authorId, String status, Pageable pageable);

    Page<DocumentJpaEntity> findBySpaceIdAndAuthorId(Long spaceId, Long authorId, Pageable pageable);

    long countBySpaceIdAndAuthorIdAndStatusNot(Long spaceId, Long authorId, String status);

    long countBySpaceIdAndAuthorId(Long spaceId, Long authorId);

    List<DocumentJpaEntity> findByStatusNot(String status);

    List<DocumentJpaEntity> findAllBySpaceIdIn(java.util.Set<Long> spaceIds);

    List<DocumentJpaEntity> findAllBySpaceIdInAndStatusNot(java.util.Set<Long> spaceIds, String status);

    List<DocumentJpaEntity> findBySpaceIdIn(Collection<Long> spaceIds);

    List<DocumentJpaEntity> findBySpaceIdInAndStatusNot(Collection<Long> spaceIds, String status);
    
    List<DocumentJpaEntity> findByAuthorId(Long authorId);
    @org.springframework.data.jpa.repository.Query(value = "SELECT count(*) FROM documents WHERE title = ?1 AND space_id = ?2 AND parent_document_id = ?3", nativeQuery = true)
    long countByTitleAndSpaceIdAndParentId(String title, Long spaceId, Long parentId);

    java.util.Optional<DocumentJpaEntity> findBySpaceIdAndTitle(Long spaceId, String title);

    @Query(value = """
            SELECT * FROM documents d
            WHERE d.status <> 'Deleted'
              AND d.title ILIKE CONCAT('%', :query, '%')
              AND (
                    d.created_at BETWEEN :effectiveFrom AND :effectiveTo
                    OR d.updated_at BETWEEN :effectiveFrom AND :effectiveTo
                  )
            ORDER BY d.updated_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM documents d
            WHERE d.status <> 'Deleted'
              AND d.title ILIKE CONCAT('%', :query, '%')
              AND (
                    d.created_at BETWEEN :effectiveFrom AND :effectiveTo
                    OR d.updated_at BETWEEN :effectiveFrom AND :effectiveTo
                  )
            """,
            nativeQuery = true)
    Page<DocumentJpaEntity> searchByTitle(@Param("query") String query,
                                          @Param("effectiveFrom") LocalDateTime effectiveFrom,
                                          @Param("effectiveTo") LocalDateTime effectiveTo,
                                          Pageable pageable);

    @Query(value = """
            SELECT * FROM documents d
            WHERE d.status <> 'Deleted'
              AND d.space_id IN (:spaceIds)
              AND d.title ILIKE CONCAT('%', :query, '%')
              AND (
                    d.created_at BETWEEN :effectiveFrom AND :effectiveTo
                    OR d.updated_at BETWEEN :effectiveFrom AND :effectiveTo
                  )
            ORDER BY d.updated_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM documents d
            WHERE d.status <> 'Deleted'
              AND d.space_id IN (:spaceIds)
              AND d.title ILIKE CONCAT('%', :query, '%')
              AND (
                    d.created_at BETWEEN :effectiveFrom AND :effectiveTo
                    OR d.updated_at BETWEEN :effectiveFrom AND :effectiveTo
                  )
            """,
            nativeQuery = true)
    Page<DocumentJpaEntity> searchByTitleInSpaces(@Param("spaceIds") Collection<Long> spaceIds,
                                                  @Param("query") String query,
                                                  @Param("effectiveFrom") LocalDateTime effectiveFrom,
                                                  @Param("effectiveTo") LocalDateTime effectiveTo,
                                                  Pageable pageable);

    @org.springframework.data.jpa.repository.Query(value = "SELECT count(*) FROM documents WHERE title = ?1 AND space_id = ?2 AND parent_document_id IS NULL", nativeQuery = true)
    long countByTitleAndSpaceIdAndNoParent(String title, Long spaceId);
    
    @org.springframework.data.jpa.repository.Query(value = """
        WITH RECURSIVE Ancestors AS (
            SELECT parent_document_id FROM documents WHERE id = :documentId
            UNION ALL
            SELECT d.parent_document_id FROM documents d
            INNER JOIN Ancestors a ON d.id = a.parent_document_id
        )
        SELECT parent_document_id FROM Ancestors WHERE parent_document_id IS NOT NULL
        """, nativeQuery = true)
    List<Long> findAncestorIds(@org.springframework.data.repository.query.Param("documentId") Long documentId);

    @org.springframework.data.jpa.repository.Query(value = "SELECT DISTINCT u.* FROM users u JOIN documents d ON u.id = d.author_id WHERE d.space_id IN :spaceIds", nativeQuery = true)
    List<com.knowledgebase.infrastructure.persistence.entity.UserJpaEntity> findDistinctAuthorsBySpaceIds(@org.springframework.data.repository.query.Param("spaceIds") java.util.Set<Long> spaceIds);

    boolean existsByParentDocumentId(Long parentId);
}

