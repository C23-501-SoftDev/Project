package com.knowledgebase.domain.model;

/**
 * Глобальная роль пользователя в системе.
 *
 * Определяет базовый набор доступных функций (первый уровень RBAC).
 * Дополнительные права на уровне пространств задаются через SpacePermission.
 *
 * Логика доступа:
 * - GUEST  → минимальные права, только просмотр публичных ресурсов
 * - READER → чтение документов в разрешённых пространствах
 * - EDITOR → создание и редактирование документов (с WRITE/OWNER правами)
 *
 * Доступ к админ-панели определяется отдельным флагом isAdmin в User,
 * а не глобальной ролью.
 */
public enum GlobalRole {

    /**
     * Гость — минимальные права доступа.
     * Только просмотр публичных ресурсов.
     */
    GUEST("Guest"),

    /**
     * Читатель — только чтение документов в разрешённых пространствах.
     * Не может создавать или редактировать документы.
     */
    READER("Reader"),

    /**
     * Редактор — может создавать и редактировать документы,
     * но только в пространствах с правами WRITE или OWNER.
     */
    EDITOR("Editor");

    /** Значение в БД (используется в Liquibase CHECK constraint) */
    private final String dbValue;

    GlobalRole(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    /**
     * Возвращает роль по значению из БД.
     * Добавлен fallback для обратной совместимости: "Admin" → EDITOR.
     * @throws IllegalArgumentException если значение не найдено
     */
    public static GlobalRole fromDbValue(String dbValue) {
        for (GlobalRole role : values()) {
            if (role.dbValue.equals(dbValue)) {
                return role;
            }
        }
        // Fallback для обратной совместимости JWT (старые токены с "Admin")
        if ("Admin".equals(dbValue)) {
            return EDITOR;
        }
        throw new IllegalArgumentException("Неизвестная роль: " + dbValue);
    }

    /**
     * Возвращает роль для использования в Spring Security
     * (с префиксом ROLE_ — например, ROLE_READER).
     */
    public String getSpringSecurityRole() {
        return "ROLE_" + this.name();
    }
}
