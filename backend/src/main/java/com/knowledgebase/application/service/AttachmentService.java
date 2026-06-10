package com.knowledgebase.application.service;

import com.knowledgebase.domain.exception.AttachmentNotFoundException;
import com.knowledgebase.domain.exception.AttachmentValidationException;
import com.knowledgebase.domain.exception.DocumentNotFoundException;
import com.knowledgebase.domain.model.Attachment;
import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.repository.AttachmentFileStorageRepository;
import com.knowledgebase.domain.repository.AttachmentRepository;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Сервис управления вложениями документов.
 *
 * <p>Отвечает за валидацию, сохранение, удаление и выдачу вложений. Сервис
 * использует {@code AttachmentRepository} для работы с метаданными и
 * {@code AttachmentFileStorageRepository} для хранения/чтения файлов. Также
 * обращается к {@code DocumentService} для проверки существования документа
 * и {@code PermissionService} для проверки прав доступа.</p>
 *
 * <p>Исключения:
 * <ul>
 *   <li>{@link com.knowledgebase.domain.exception.AttachmentNotFoundException} —
 *   когда запрошенное вложение не найдено;</li>
 *   <li>{@link com.knowledgebase.domain.exception.AttachmentValidationException} —
 *   при ошибках валидации или IO;</li>
 *   <li>{@link com.knowledgebase.domain.exception.DocumentNotFoundException} —
 *   когда документ не найден.</li>
 * </ul>
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final AttachmentRepository attachmentRepository;
    private final AttachmentFileStorageRepository storageRepository;
    private final DocumentService documentService;
    private final PermissionService permissionService;

    private final long maxFileSizeBytes;
    private final Set<String> allowedExtensions;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             AttachmentFileStorageRepository storageRepository,
                             DocumentService documentService,
                             PermissionService permissionService,
                             @Value("${app.storage.attachments.max-size-bytes:10485760}") long maxFileSizeBytes,
                             @Value("${app.storage.attachments.allowed-extensions:md,png,jpg,jpeg,gif,pdf,txt,docx,xlsx,pptx,zip}") String allowedExtensions) {
        this.attachmentRepository = attachmentRepository;
        this.storageRepository = storageRepository;
        this.documentService = documentService;
        this.permissionService = permissionService;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.allowedExtensions = parseAllowedExtensions(allowedExtensions);
    }

    public boolean canDownload(Long userId, boolean isAdmin, Long attachmentId) {
        if (userId == null) {
            return false;
        }

        Attachment attachment = getAttachmentById(attachmentId);
        Document document = documentService.getDocumentById(attachment.getDocumentId());
        return permissionService.canRead(userId, isAdmin, document.getSpaceId());
    }

    public List<Attachment> getAttachmentsForDocument(Long documentId) {
        documentService.getDocumentById(documentId);
        return attachmentRepository.findByDocumentId(documentId, false);
    }

    public Attachment getAttachmentById(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
    }

    /**
     * Загружает и сохраняет список файлов как вложения для указанного документа.
     * <p>Проверяет существование документа, валидацию входных файлов (наличие,
     * размер, расширение), сохраняет файлы во внешнем файловом хранилище и
     * сохраняет метаданные во {@code AttachmentRepository}. В случае ошибки
     * сохраняет уже записанные файлы и пробрасывает исключение.</p>
     *
     * @param documentId id документа
     * @param files список файлов для загрузки
     * @param uploadedBy id пользователя, загрузившего файлы
     * @return список сохранённых объектов {@link Attachment}
     * @throws DocumentNotFoundException если документ не найден
     * @throws AttachmentValidationException при ошибке валидации или IO
     */
    @Transactional
    public List<Attachment> uploadAttachments(Long documentId, List<MultipartFile> files, Long uploadedBy) {
        Document document = documentService.getDocumentById(documentId);
        validateFiles(files);

        List<Attachment> savedAttachments = new ArrayList<>();
        List<String> storedPaths = new ArrayList<>();

        try {
            for (MultipartFile file : files) {
                validateFile(file);

                String originalFilename = normalizeOriginalFilename(file.getOriginalFilename());
                String storagePath = buildStoragePath(document.getId(), originalFilename);
                try (InputStream inputStream = file.getInputStream()) {
                    storageRepository.store(storagePath, inputStream);
                }
                storedPaths.add(storagePath);

                Attachment attachment = Attachment.create(
                        document.getId(),
                        null,
                        originalFilename,
                        file.getContentType(),
                        file.getSize(),
                        storagePath,
                        uploadedBy
                );

                savedAttachments.add(attachmentRepository.save(attachment));
            }

            return savedAttachments;
        } catch (IOException ex) {
            cleanupStoredFiles(storedPaths);
            throw new AttachmentValidationException(ex.getMessage());
        } catch (RuntimeException ex) {
            cleanupStoredFiles(storedPaths);
            throw ex;
        }
    }

    /**
     * Удаляет вложение: проверяет принадлежность вложения указанному документу,
     * удаляет запись из репозитория и удаляет файл из файлового хранилища.
     *
     * @param documentId id документа
     * @param attachmentId id вложения
     * @throws AttachmentNotFoundException если вложение не найдено или не принадлежит документу
     * @throws AttachmentValidationException если при удалении файла в хранилище произошла ошибка
     */
    @Transactional
    public void deleteAttachment(Long documentId, Long attachmentId) {
        documentService.getDocumentById(documentId);
        Attachment attachment = getAttachmentById(attachmentId);

        if (!documentId.equals(attachment.getDocumentId())) {
            throw new AttachmentNotFoundException(attachmentId);
        }

        attachmentRepository.deleteById(attachmentId);
        try {
            storageRepository.delete(attachment.getStoragePath());
        } catch (IOException ex) {
            throw new AttachmentValidationException("Не удалось удалить файл вложения: " + ex.getMessage());
        }
    }

    /**
     * Возвращает метаданные вложения с проверкой принадлежности к документу.
     *
     * @param documentId id документа
     * @param attachmentId id вложения
     * @return найденное {@link Attachment}
     * @throws AttachmentNotFoundException если вложение отсутствует или не принадлежит документу
     */
    public Attachment getAttachment(Long documentId, Long attachmentId) {
        documentService.getDocumentById(documentId);
        Attachment attachment = getAttachmentById(attachmentId);

        if (!documentId.equals(attachment.getDocumentId())) {
            throw new AttachmentNotFoundException(attachmentId);
        }

        return attachment;
    }

    /**
     * Открывает ресурс вложения для скачивания.
     *
     * @param attachmentId id вложения
     * @return {@link AttachmentDownloadData} с метаданными и SPRING {@link Resource}
     * @throws AttachmentNotFoundException если вложение не найдено
     * @throws AttachmentValidationException при ошибке открытия файла в хранилище
     */
    public AttachmentDownloadData downloadAttachment(Long attachmentId) {
        Attachment attachment = getAttachmentById(attachmentId);
        try {
            Resource resource = new InputStreamResource(storageRepository.open(attachment.getStoragePath()));
            return new AttachmentDownloadData(attachment, resource);
        } catch (IOException ex) {
            throw new AttachmentValidationException("Не удалось открыть файл вложения: " + ex.getMessage());
        }
    }

    /** Проверяет, что список файлов не пустой. */
    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new AttachmentValidationException("Необходимо выбрать хотя бы один файл для загрузки");
        }
    }

    /**
     * Проверяет единичный файл: не пустой, размер и расширение.
     * @throws AttachmentValidationException при нарушении ограничений
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AttachmentValidationException("Файл не может быть пустым");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new AttachmentValidationException("Файл превышает допустимый размер. Максимум: " + formatMaxSize());
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (extension.isBlank() || !allowedExtensions.contains(extension)) {
            throw new AttachmentValidationException("Недопустимый тип файла. Допустимые типы: " + formatAllowedExtensions());
        }
    }

    /** Формирует уникальный путь для хранения файла во внешнем хранилище. */
    private String buildStoragePath(Long documentId, String originalFilename) {
        String safeName = originalFilename.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return String.join("/", "attachments", "document-" + documentId, UUID.randomUUID() + "-" + safeName);
    }

    /** Нормализует и валидирует исходное имя файла. */
    private String normalizeOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new AttachmentValidationException("Имя файла не может быть пустым");
        }
        return originalFilename.trim();
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private Set<String> parseAllowedExtensions(String configuredExtensions) {
        Set<String> extensions = new HashSet<>();
        for (String value : configuredExtensions.split(",")) {
            String trimmed = value.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isBlank()) {
                extensions.add(trimmed.startsWith(".") ? trimmed.substring(1) : trimmed);
            }
        }
        return extensions;
    }

    /** Форматирует допустимые расширения в строку, например ".jpg, .png". */
    private String formatAllowedExtensions() {
        return allowedExtensions.stream()
                .sorted()
                .map(ext -> "." + ext)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private String formatMaxSize() {
        double megabytes = maxFileSizeBytes / 1024.0 / 1024.0;
        return String.format(Locale.ROOT, "%.1f MB", megabytes);
    }

    /**
     * Пытается удалить список ранее сохранённых путей — используется при откате
     * после ошибки загрузки нескольких файлов.
     */
    private void cleanupStoredFiles(List<String> storedPaths) {
        for (String storedPath : storedPaths) {
            try {
                storageRepository.delete(storedPath);
            } catch (IOException cleanupEx) {
                log.warn("Не удалось удалить временный файл вложения {}: {}", storedPath, cleanupEx.getMessage());
            }
        }
    }

    /**
     * DTO: содержит метаданные вложения и {@link Resource} для передачи в ответе.
     */
    public record AttachmentDownloadData(Attachment attachment, Resource resource) {}
}
