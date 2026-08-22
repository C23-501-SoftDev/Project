package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.AuditLogEntry;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Интерфейс репозитория журнала аудита (Domain Layer) — US4.1.5.
 *
 * Реализация: {@link com.knowledgebase.infrastructure.persistence.repository.AuditLogRepositoryImpl}
 */
public interface AuditLogRepository {

    /**
     * Сохраняет запись аудита.
     */
    AuditLogEntry save(AuditLogEntry entry);

    /**
     * Возвращает страницу записей журнала с фильтрацией.
     * Любой из фильтров может быть null (не применяется).
     *
     * @param userId     фильтр по пользователю
     * @param actionType фильтр по типу действия
     * @param dateFrom   нижняя граница по времени (включительно)
     * @param dateTo     верхняя граница по времени (включительно)
     * @param page       номер страницы (0-based)
     * @param size       размер страницы
     * @return записи, отсортированные по времени по убыванию
     */
    List<AuditLogEntry> find(Long userId, String actionType,
                             LocalDateTime dateFrom, LocalDateTime dateTo,
                             int page, int size);

    /**
     * Возвращает количество записей по тем же фильтрам, что и {@link #find}.
     */
    long count(Long userId, String actionType, LocalDateTime dateFrom, LocalDateTime dateTo);
}
