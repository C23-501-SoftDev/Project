package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.infrastructure.persistence.entity.AuditLogJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * Spring Data JPA репозиторий для AuditLogJpaEntity (US4.1.5).
 * Используется внутри AuditLogRepositoryImpl.
 */
public interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, Long> {

    /**
     * Страница журнала с необязательными фильтрами по пользователю,
     * типу действия и диапазону дат.
     */
    @Query("""
            SELECT a FROM AuditLogJpaEntity a
            WHERE (:userId IS NULL OR a.userId = :userId)
            AND (:actionType IS NULL OR a.actionType = :actionType)
            AND (CAST(:dateFrom AS timestamp) IS NULL OR a.createdAt >= :dateFrom)
            AND (CAST(:dateTo AS timestamp) IS NULL OR a.createdAt <= :dateTo)
            """)
    Page<AuditLogJpaEntity> findFiltered(@Param("userId") Long userId,
                                         @Param("actionType") String actionType,
                                         @Param("dateFrom") LocalDateTime dateFrom,
                                         @Param("dateTo") LocalDateTime dateTo,
                                         Pageable pageable);
}
