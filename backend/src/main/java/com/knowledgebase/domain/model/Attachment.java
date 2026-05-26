package com.knowledgebase.domain.model;

import java.time.LocalDateTime;

/**
 * Доменная модель вложения документа.
 */
public class Attachment {

    private Long id;
    private Long documentId;
    private Long versionId;
    private String filename;
    private String contentType;
    private long sizeBytes;
    private String storagePath;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;

    private Attachment() {}

    public static Attachment create(Long documentId,
                                    Long versionId,
                                    String filename,
                                    String contentType,
                                    long sizeBytes,
                                    String storagePath,
                                    Long uploadedBy) {
        Attachment attachment = new Attachment();
        attachment.documentId = documentId;
        attachment.versionId = versionId;
        attachment.filename = filename;
        attachment.contentType = contentType;
        attachment.sizeBytes = sizeBytes;
        attachment.storagePath = storagePath;
        attachment.uploadedBy = uploadedBy;
        attachment.uploadedAt = LocalDateTime.now();
        return attachment;
    }

    public static Attachment restore(Long id,
                                     Long documentId,
                                     Long versionId,
                                     String filename,
                                     String contentType,
                                     long sizeBytes,
                                     String storagePath,
                                     Long uploadedBy,
                                     LocalDateTime uploadedAt) {
        Attachment attachment = new Attachment();
        attachment.id = id;
        attachment.documentId = documentId;
        attachment.versionId = versionId;
        attachment.filename = filename;
        attachment.contentType = contentType;
        attachment.sizeBytes = sizeBytes;
        attachment.storagePath = storagePath;
        attachment.uploadedBy = uploadedBy;
        attachment.uploadedAt = uploadedAt;
        return attachment;
    }

    public Long getId() { return id; }
    public Long getDocumentId() { return documentId; }
    public Long getVersionId() { return versionId; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getStoragePath() { return storagePath; }
    public Long getUploadedBy() { return uploadedBy; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
