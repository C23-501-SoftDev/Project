package com.knowledgebase.infrastructure.storage;

import com.knowledgebase.application.service.StorageService;
import com.knowledgebase.domain.repository.AttachmentFileStorageRepository;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Потоковое сохранение вложений на файловом хранилище.
 *
 * В проде `app.storage.blob.path` должен указывать на SMB-mounted directory.
 */
@Repository
public class FileSystemAttachmentFileStorageRepository implements AttachmentFileStorageRepository {

    private final StorageService storageService;

    public FileSystemAttachmentFileStorageRepository(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public void store(String storagePath, InputStream inputStream) throws IOException {
        Path target = storageService.getBlobStoragePath().resolve(storagePath).normalize();
        Files.createDirectories(target.getParent());
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public InputStream open(String storagePath) throws IOException {
        Path target = storageService.getBlobStoragePath().resolve(storagePath).normalize();
        return Files.newInputStream(target);
    }

    @Override
    public void delete(String storagePath) throws IOException {
        Path target = storageService.getBlobStoragePath().resolve(storagePath).normalize();
        Files.deleteIfExists(target);
    }
}
