package com.knowledgebase.application.service;

import com.knowledgebase.domain.model.AuditLogEntry;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис аудита действий системы (Application Layer) — US4.1.5.
 *
 * Записывает в журнал критические действия пользователей: операции над
 * документами, пространствами, пользователями, группами и правами доступа.
 *
 * Автор действия и IP-адрес определяются автоматически из текущего
 * SecurityContext и HTTP-запроса. Запись выполняется в той же транзакции,
 * что и бизнес-операция: если операция откатилась — записи в журнале не будет.
 *
 * Сбой записи аудита никогда не ломает бизнес-операцию (ошибки гасятся и логируются).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    // Типы ресурсов
    public static final String RESOURCE_DOCUMENT = "DOCUMENT";
    public static final String RESOURCE_SPACE = "SPACE";
    public static final String RESOURCE_USER = "USER";
    public static final String RESOURCE_GROUP = "GROUP";
    public static final String RESOURCE_PERMISSION = "PERMISSION";

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Записывает действие в журнал аудита.
     * Автор и IP берутся из текущего контекста запроса.
     *
     * @param actionType   тип действия (например, DOCUMENT_CREATED)
     * @param resourceType тип ресурса (DOCUMENT, SPACE, USER, GROUP, PERMISSION)
     * @param resourceId   ID затронутого ресурса (может быть null)
     * @param details      детали действия, например старые/новые значения (может быть null)
     */
    public void record(String actionType, String resourceType, Long resourceId, String details) {
        try {
            User current = currentUser();
            AuditLogEntry entry = AuditLogEntry.create(
                    current != null ? current.getId() : null,
                    current != null ? current.getLogin() : "system",
                    actionType,
                    resourceType,
                    resourceId,
                    details,
                    currentIpAddress());
            auditLogRepository.save(entry);
        } catch (RuntimeException ex) {
            // Аудит не должен ломать бизнес-операцию.
            log.error("Не удалось записать событие аудита {}: {}", actionType, ex.getMessage());
        }
    }

    /**
     * Возвращает страницу журнала аудита с фильтрами (для ADMIN).
     */
    @Transactional(readOnly = true)
    public List<AuditLogEntry> getEntries(Long userId, String actionType,
                                          LocalDateTime dateFrom, LocalDateTime dateTo,
                                          int page, int size) {
        return auditLogRepository.find(userId, actionType, dateFrom, dateTo, page, size);
    }

    /**
     * Возвращает количество записей журнала по фильтрам (для пагинации).
     */
    @Transactional(readOnly = true)
    public long countEntries(Long userId, String actionType,
                             LocalDateTime dateFrom, LocalDateTime dateTo) {
        return auditLogRepository.count(userId, actionType, dateFrom, dateTo);
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    private String currentIpAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }
}
