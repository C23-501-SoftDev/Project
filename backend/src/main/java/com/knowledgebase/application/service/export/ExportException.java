package com.knowledgebase.application.service.export;

/** Controlled export errors never expose physical paths or foreign attachment metadata. */
public class ExportException extends RuntimeException {
    public enum Code { LIMIT_EXCEEDED, ATTACHMENT_UNAVAILABLE, SOURCE_CHANGED }
    private final Code code;
    public ExportException(Code code) {
        super(switch (code) {
            case LIMIT_EXCEEDED -> "Экспорт превышает допустимый размер или количество файлов/изображений.";
            case ATTACHMENT_UNAVAILABLE -> "Файл вложения недоступен. Обновите документ или повторно загрузите файл.";
            case SOURCE_CHANGED -> "Документ, вложения или права доступа изменились. Повторите экспорт.";
        });
        this.code = code;
    }
    public Code getCode() { return code; }
}
