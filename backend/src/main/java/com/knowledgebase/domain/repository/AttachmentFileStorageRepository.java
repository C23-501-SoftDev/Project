package com.knowledgebase.domain.repository;

import java.io.IOException;
import java.io.InputStream;

/**
 * Контракт для потокового сохранения файлов вложений в blob-хранилище.
 */
public interface AttachmentFileStorageRepository {

    void store(String storagePath, InputStream inputStream) throws IOException;

    InputStream open(String storagePath) throws IOException;

    void delete(String storagePath) throws IOException;
}
