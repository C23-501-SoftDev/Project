package com.knowledgebase.domain.exception;

/** The requested comparison exceeds the configured response line limit. */
public class DocumentDiffTooLargeException extends DomainException {
    public DocumentDiffTooLargeException(int maxLines) {
        super("Сравнение содержит больше " + maxLines + " строк");
    }

    public DocumentDiffTooLargeException(long maxBytes) {
        super("Размер версии для сравнения превышает " + maxBytes + " байт");
    }
}
