package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.model.AuditLogEntry;
import com.knowledgebase.domain.repository.AuditLogRepository;
import com.knowledgebase.infrastructure.persistence.entity.AuditLogJpaEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Реализация доменного репозитория AuditLogRepository через Spring Data JPA (US4.1.5).
 */
@Repository
public class AuditLogRepositoryImpl implements AuditLogRepository {

    private final AuditLogJpaRepository jpaRepository;

    public AuditLogRepositoryImpl(AuditLogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AuditLogEntry save(AuditLogEntry entry) {
        AuditLogJpaEntity entity = toJpaEntity(entry);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public List<AuditLogEntry> find(Long userId, String actionType,
                                    LocalDateTime dateFrom, LocalDateTime dateTo,
                                    int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return jpaRepository.findFiltered(userId, actionType, dateFrom, dateTo, pageable)
                .getContent()
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long count(Long userId, String actionType, LocalDateTime dateFrom, LocalDateTime dateTo) {
        return jpaRepository.findFiltered(userId, actionType, dateFrom, dateTo,
                PageRequest.of(0, 1)).getTotalElements();
    }

    private AuditLogJpaEntity toJpaEntity(AuditLogEntry entry) {
        AuditLogJpaEntity entity = new AuditLogJpaEntity();
        entity.setId(entry.getId());
        entity.setCreatedAt(entry.getCreatedAt());
        entity.setUserId(entry.getUserId());
        entity.setUserLogin(entry.getUserLogin());
        entity.setActionType(entry.getActionType());
        entity.setResourceType(entry.getResourceType());
        entity.setResourceId(entry.getResourceId());
        entity.setDetails(entry.getDetails());
        entity.setIpAddress(entry.getIpAddress());
        return entity;
    }

    private AuditLogEntry toDomain(AuditLogJpaEntity entity) {
        return AuditLogEntry.restore(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getUserId(),
                entity.getUserLogin(),
                entity.getActionType(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getDetails(),
                entity.getIpAddress());
    }
}
