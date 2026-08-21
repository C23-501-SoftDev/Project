

package com.knowledgebase.infrastructure.storage;

import com.knowledgebase.domain.repository.AttachmentFileStorageRepository;
import com.knowledgebase.domain.service.BlobStorageService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;

/**
 * Реализация AttachmentFileStorageRepository через абстракцию BlobStorageService (MinIO / S3).
 * Обеспечивает хранение бинарных данных в объектном хранилище отдельно от БД и Git.
 */
@Primary
@Repository
public class BlobStorageAttachmentFileStorageRepository implements AttachmentFileStorageRepository {

    private final BlobStorageService blobStorageService;

    public BlobStorageAttachmentFileStorageRepository(BlobStorageService blobStorageService) {
        this.blobStorageService = blobStorageService;
    }

    @Override
    public void store(String storagePath, InputStream inputStream) throws IOException {
        try {
            blobStorageService.upload(storagePath, inputStream);
        } catch (Exception e) {
            throw new IOException(
                    "Не удалось сохранить файл в Blob Storage: " + storagePath,
                    e
            );
        }
    }

    @Override
    public InputStream open(String storagePath) throws IOException {
        try {
            return blobStorageService.getInputStream(storagePath);
        } catch (Exception e) {
            throw new IOException(
                    "Не удалось открыть файл из Blob Storage: " + storagePath,
                    e
            );
        }
    }

    @Override
    public void delete(String storagePath) throws IOException {
        try {
            if (blobStorageService.exists(storagePath)) {
                blobStorageService.delete(storagePath);
            }
        } catch (Exception e) {
            throw new IOException(
                    "Не удалось удалить файл из Blob Storage: " + storagePath,
                    e
            );
        }
    }
}