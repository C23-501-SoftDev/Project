package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.infrastructure.persistence.entity.DocumentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentJpaRepository extends JpaRepository<DocumentJpaEntity, Long> {
    
    List<DocumentJpaEntity> findBySpaceId(Long spaceId);
    
    List<DocumentJpaEntity> findBySpaceIdAndStatusNot(Long spaceId, String status);
    
    List<DocumentJpaEntity> findByAuthorId(Long authorId);
}
