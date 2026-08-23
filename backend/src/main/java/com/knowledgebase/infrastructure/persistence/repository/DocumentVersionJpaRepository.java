package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.infrastructure.persistence.entity.DocumentVersionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentVersionJpaRepository extends JpaRepository<DocumentVersionJpaEntity, Long> {
    Optional<DocumentVersionJpaEntity> findByDocumentIdAndGitHash(Long documentId, String gitHash);

    List<DocumentVersionJpaEntity> findByDocumentIdOrderByCreatedAtAsc(Long documentId);
}
