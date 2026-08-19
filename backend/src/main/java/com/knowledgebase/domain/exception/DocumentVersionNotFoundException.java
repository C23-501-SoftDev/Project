package com.knowledgebase.domain.exception;

/** A requested Git version is not registered for the current document. */
public class DocumentVersionNotFoundException extends DomainException {
    public DocumentVersionNotFoundException(Long documentId, String gitHash) {
        super("Версия " + gitHash + " для документа " + documentId + " не найдена");
    }
}
