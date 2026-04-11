package com.knowledgebase.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Сервис для работы с файловыми хранилищами (Git и Blob).
 * 
 * MVP: Предоставляет пути к хранилищам для использования в других компонентах.
 * В будущем здесь будет реализована логика сохранения/чтения файлов.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    @Value("${app.storage.git.path}")
    private String gitRepoPath;

    @Value("${app.storage.blob.path}")
    private String blobStoragePath;

    /**
     * Возвращает путь к корневой директории Git-репозитория.
     */
    public Path getGitRepoPath() {
        return Paths.get(gitRepoPath);
    }

    /**
     * Возвращает путь к директории для хранения документов (.md файлов).
     */
    public Path getDocumentsPath() {
        return getGitRepoPath();
    }

    /**
     * Возвращает путь к Blob-хранилищу.
     */
    public Path getBlobStoragePath() {
        return Paths.get(blobStoragePath);
    }

    /**
     * Возвращает путь к директории для изображений.
     */
    public Path getImagesPath() {
        return Paths.get(blobStoragePath, "images");
    }

    /**
     * Возвращает путь к директории для вложений.
     */
    public Path getAttachmentsPath() {
        return Paths.get(blobStoragePath, "attachments");
    }

    /**
     * Проверяет, что все хранилища доступны для чтения и записи.
     * Используется для health-check.
     */
    public boolean isStorageAccessible() {
        try {
            Path gitPath = getGitRepoPath();
            Path blobPath = getBlobStoragePath();
            
            boolean gitAccessible = gitPath.toFile().canRead() && gitPath.toFile().canWrite();
            boolean blobAccessible = blobPath.toFile().canRead() && blobPath.toFile().canWrite();
            
            if (!gitAccessible) {
                log.warn("Git-репозиторий недоступен: {}", gitPath);
            }
            if (!blobAccessible) {
                log.warn("Blob-хранилище недоступно: {}", blobPath);
            }
            
            return gitAccessible && blobAccessible;
        } catch (Exception e) {
            log.error("Ошибка проверки доступности хранилищ", e);
            return false;
        }
    }
}
