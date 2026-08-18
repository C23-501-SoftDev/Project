package com.knowledgebase.infrastructure.storage;

import com.knowledgebase.domain.service.BlobStorageService;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * Реализация BlobStorageService на базе MinIO (S3-compatible storage) (US1.4.1).
 */
@Service
@ConditionalOnProperty(name = "app.storage.blob.provider", havingValue = "minio", matchIfMissing = true)
public class MinioBlobStorageService implements BlobStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioBlobStorageService.class);

    private final MinioClient minioClient;
    private final String bucketName;

    public MinioBlobStorageService(
            @Value("${app.storage.blob.minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${app.storage.blob.minio.access-key:minioadmin}") String accessKey,
            @Value("${app.storage.blob.minio.secret-key:minioadmin}") String secretKey,
            @Value("${app.storage.blob.minio.bucket:knowledge-base-attachments}") String bucketName) {
        this.bucketName = bucketName;
        log.info("Инициализация MinioClient с endpoint: {}, bucket: {}", endpoint, bucketName);
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @PostConstruct
    public void initBucket() {
        try {
            log.info("Проверка существования bucket в MinIO: {}", bucketName);
            boolean found = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!found) {
                log.info("Bucket '{}' не найден. Создание нового bucket...", bucketName);
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                log.info("Bucket '{}' успешно создан", bucketName);
            } else {
                log.info("Bucket '{}' успешно найден в MinIO", bucketName);
            }
        } catch (Exception e) {
            log.error("Ошибка при проверке/создании bucket в MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось инициализировать MinIO bucket: " + bucketName, e);
        }
    }

    @Override
    public void upload(String path, InputStream inputStream, long size, String contentType) {
        try {
            long objSize = size > 0 ? size : -1;
            String type = (contentType != null && !contentType.isBlank()) ? contentType : "application/octet-stream";
            log.debug("Загрузка файла в MinIO: bucket={}, path={}, size={}, contentType={}", bucketName, path, objSize, type);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .stream(inputStream, objSize, 10485760)
                            .contentType(type)
                            .build()
            );
            log.info("Файл успешно загружен в MinIO: {}", path);
        } catch (Exception e) {
            log.error("Ошибка загрузки файла в MinIO по пути {}: {}", path, e.getMessage(), e);
            throw new RuntimeException("Не удалось загрузить файл в MinIO: " + path, e);
        }
    }

    @Override
    public InputStream getInputStream(String path) {
        try {
            log.debug("Получение потока файла из MinIO: bucket={}, path={}", bucketName, path);
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .build()
            );
        } catch (Exception e) {
            log.error("Ошибка получения файла из MinIO по пути {}: {}", path, e.getMessage(), e);
            throw new RuntimeException("Не удалось получить файл из MinIO: " + path, e);
        }
    }

    @Override
    public void delete(String path) {
        try {
            log.debug("Удаление файла из MinIO: bucket={}, path={}", bucketName, path);
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .build()
            );
            log.info("Файл успешно удален из MinIO: {}", path);
        } catch (Exception e) {
            log.error("Ошибка удаления файла из MinIO по пути {}: {}", path, e.getMessage(), e);
            throw new RuntimeException("Не удалось удалить файл из MinIO: " + path, e);
        }
    }

    @Override
    public boolean exists(String path) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            String code = e.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NotFound".equals(code) || "NoSuchBucket".equals(code)) {
                return false;
            }
            log.error("Ошибка S3 при проверке существования объекта {}: {}", path, e.getMessage(), e);
            throw new RuntimeException("Ошибка проверки существования файла в MinIO: " + path, e);
        } catch (Exception e) {
            log.error("Ошибка подключения или выполнения при проверке объекта {}: {}", path, e.getMessage(), e);
            throw new RuntimeException("Ошибка проверки существования файла в MinIO: " + path, e);
        }
    }
}