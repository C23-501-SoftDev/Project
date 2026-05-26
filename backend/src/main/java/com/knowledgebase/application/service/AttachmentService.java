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

    public Attachment getAttachment(Long documentId, Long attachmentId) {
        documentService.getDocumentById(documentId);
        Attachment attachment = getAttachmentById(attachmentId);

        if (!documentId.equals(attachment.getDocumentId())) {
            throw new AttachmentNotFoundException(attachmentId);
        }

        return attachment;
    }

    public AttachmentDownloadData downloadAttachment(Long attachmentId) {
        Attachment attachment = getAttachmentById(attachmentId);
        try {
            Resource resource = new InputStreamResource(storageRepository.open(attachment.getStoragePath()));
            return new AttachmentDownloadData(attachment, resource);
        } catch (IOException ex) {
            throw new AttachmentValidationException("Не удалось открыть файл вложения: " + ex.getMessage());
        }
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new AttachmentValidationException("Необходимо выбрать хотя бы один файл для загрузки");
        }
    }

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

    private String buildStoragePath(Long documentId, String originalFilename) {
        String safeName = originalFilename.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return String.join("/", "attachments", "document-" + documentId, UUID.randomUUID() + "-" + safeName);
    }

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

    private void cleanupStoredFiles(List<String> storedPaths) {
        for (String storedPath : storedPaths) {
            try {
                storageRepository.delete(storedPath);
            } catch (IOException cleanupEx) {
                log.warn("Не удалось удалить временный файл вложения {}: {}", storedPath, cleanupEx.getMessage());
            }
        }
    }

    public record AttachmentDownloadData(Attachment attachment, Resource resource) {}
}
