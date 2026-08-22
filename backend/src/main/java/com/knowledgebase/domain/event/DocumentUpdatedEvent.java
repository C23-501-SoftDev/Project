package com.knowledgebase.domain.event;

/**
 * Событие изменения документа.
 *
 * Публикуется после успешного обновления документа (метаданные и/или контент).
 * Слушатели могут уведомить участников пространства об изменении.
 *
 * @see com.knowledgebase.domain.event.DomainEvent
 */
public class DocumentUpdatedEvent extends DomainEvent {

    private final Long documentId;
    private final String documentTitle;
    private final Long spaceId;
    private final Long editorId;
    private final String editorLogin;

    public DocumentUpdatedEvent(Long documentId, String documentTitle, Long spaceId,
                                Long editorId, String editorLogin) {
        super();
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.spaceId = spaceId;
        this.editorId = editorId;
        this.editorLogin = editorLogin;
    }

    public Long getDocumentId() { return documentId; }
    public String getDocumentTitle() { return documentTitle; }
    public Long getSpaceId() { return spaceId; }
    public Long getEditorId() { return editorId; }
    public String getEditorLogin() { return editorLogin; }
}
