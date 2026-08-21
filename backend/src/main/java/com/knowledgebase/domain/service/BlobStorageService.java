package com.knowledgebase.domain.service;

import java.io.InputStream;

/**
 * Интерфейс уровня приложения для работы с Blob Storage (US1.4.1).
 * Независим от конкретного провайдера (MinIO, S3 и т.д.).
 */
public interface BlobStorageService {

    /**
     * Загружает файл в Blob Storage.
     *
     * @param path путь/ключ объекта в хранилище
     * @param inputStream потоковые данные файла
     * @param size размер файла в байтах
     * @param contentType MIME-тип файла
     */
    void upload(String path, InputStream inputStream, long size, String contentType);

    /**
     * Загружает файл в Blob Storage без явного указания размера и типа.
     */
    default void upload(String path, InputStream inputStream) {
        upload(path, inputStream, -1, "application/octet-stream");
    }
    /**
     * Получает поток для чтения файла из Blob Storage.
     *
     * @param path путь/ключ объекта в хранилище
     * @return InputStream файла
     */
    InputStream getInputStream(String path);
    /**
     * Удаляет файл из Blob Storage.
     *
     * @param path путь/ключ объекта в хранилище
     */
    void delete(String path);

    /**
     * Проверяет существование файла в Blob Storage.
     *
     * @param path путь/ключ объекта в хранилище
     * @return true, если файл существует
     */
    boolean exists(String path);
}