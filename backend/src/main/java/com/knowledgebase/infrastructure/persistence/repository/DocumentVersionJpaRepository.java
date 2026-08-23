package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.infrastructure.persistence.entity.DocumentVersionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionJpaRepository extends JpaRepository<DocumentVersionJpaEntity, Long> {
}
