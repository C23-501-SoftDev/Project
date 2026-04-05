package com.knowledgebase.domain.model;

/**
 * Глобальная роль пользователя в системе.
 *
 * Определяет базовый набор доступных функций (первый уровень RBAC).
 * Дополнительные права на уровне пространств задаются через SpacePermission.
 *
 * Логика доступа:
 * - ADMIN  → полный доступ без ограничений
 * - EDITOR → может создавать/редактировать документы, но только в пространствах
 *            с WRITE или OWNER правами
 * - READER → только чтение, никогда не может редактировать документы
 */
public enum GlobalRole {

    /**
     * Администратор — неограниченный доступ ко всем ресурсам.
     * Может управлять пользователями, пространствами, выполнять hard-delete.
     */
    ADMIN("Admin"),

    /**
     * Редактор — может создавать и редактировать документы,
     * но только в пространствах с правами WRITE или OWNER.
     */
    EDITOR("Editor"),

    /**
     * Читатель — только просмотр документов в разрешённых пространствах.
     * Не может создавать или редактировать документы.
     */
    READER("Reader");

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
     * @throws IllegalArgumentException если значение не найдено
     */
    public static GlobalRole fromDbValue(String dbValue) {
        for (GlobalRole role : values()) {
            if (role.dbValue.equals(dbValue)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Неизвестная роль: " + dbValue);
    }

    /**
     * Возвращает роль для использования в Spring Security
     * (с префиксом ROLE_ — например, ROLE_ADMIN).
     */
    public String getSpringSecurityRole() {
        return "ROLE_" + this.name();
    }
}
