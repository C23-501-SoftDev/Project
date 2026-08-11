package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.infrastructure.persistence.entity.VersionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VersionJpaRepository extends JpaRepository<VersionJpaEntity, Long> {
    List<VersionJpaEntity> findByDocumentIdOrderByCreatedAtDesc(Long documentId);
    void deleteByDocumentId(Long documentId);
}

