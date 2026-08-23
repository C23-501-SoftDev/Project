package com.knowledgebase.domain.exception;

/** A legacy version lacks the historical Git path required for an accurate diff. */
public class DocumentVersionPathUnavailableException extends DomainException {
    public DocumentVersionPathUnavailableException(Long documentId, String gitHash) {
        super("Для версии " + gitHash + " документа " + documentId
                + " не сохранен исторический путь файла; точное сравнение недоступно");
    }
}
