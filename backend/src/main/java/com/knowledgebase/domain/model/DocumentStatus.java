package com.knowledgebase.domain.model;

/**
 * Статус документа в системе.
 */
public enum DocumentStatus {

    /** Черновик — виден только автору и редакторам пространства */
    DRAFT("Draft"),

    /** Опубликован — виден всем пользователям, имеющим доступ к пространству */
    PUBLISHED("Published"),

    /** Удален — документ находится в архиве */
    DELETED("Deleted");

    private final String dbValue;

    DocumentStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static DocumentStatus fromDbValue(String dbValue) {
        for (DocumentStatus status : values()) {
            if (status.dbValue.equals(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Неизвестный статус документа: " + dbValue);
    }
}
