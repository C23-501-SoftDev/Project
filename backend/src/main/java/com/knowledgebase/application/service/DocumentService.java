package com.knowledgebase.application.service;

import com.knowledgebase.domain.exception.DocumentNotFoundException;
import com.knowledgebase.domain.exception.DocumentValidationException;
import com.knowledgebase.domain.exception.SpaceNotFoundException;
import com.knowledgebase.domain.exception.UserNotFoundException;
import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.DocumentStatus;
import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.DocumentContentRepository;
import com.knowledgebase.domain.repository.DocumentRepository;
import com.knowledgebase.domain.repository.SpaceRepository;
import com.knowledgebase.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис управления документами (Application Layer).
 * Координирует работу с БД (метаданные) и Git (контент).
 */
@Service
@Transactional(readOnly = true)
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocumentContentRepository contentRepository;
    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentContentRepository contentRepository,
                           SpaceRepository spaceRepository,
                           UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.contentRepository = contentRepository;
        this.spaceRepository = spaceRepository;
        this.userRepository = userRepository;
    }

    /**
     * Создаёт новый документ.
     * Сначала сохраняет метаданные в БД, затем контент в Git.
     *
     * @param title    заголовок
     * @param content  содержимое Markdown
     * @param spaceId  ID пространства
     * @param authorId ID автора
     * @return созданный документ
     */
    /**
     * Создаёт новый документ.
     * Сначала сохраняет метаданные в БД, затем контент в Git.
     *
     * @param title    заголовок
     * @param content  содержимое Markdown
     * @param spaceId  ID пространства
     * @param parentId ID родительского документа
     * @param authorId ID автора
     * @return созданный документ
     */
    @Transactional
    public Document createDocument(String title, String content, Long spaceId, Long parentId, Long authorId, Long templateId) {
        log.debug("Создание документа: title='{}', spaceId={}, parentId={}, authorId={}, templateId={}", title, spaceId, parentId, authorId, templateId);

        validateHierarchy(null, parentId, spaceId);
        validateTitleUniqueness(title, spaceId, parentId, null);

        if (!spaceRepository.findById(spaceId).isPresent()) {
            throw new SpaceNotFoundException(spaceId);
        }

        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new UserNotFoundException(authorId));

        String actualContent = content;
        if (templateId != null) {
            com.knowledgebase.domain.repository.TemplateRepository templateRepository = 
                com.knowledgebase.application.ApplicationContextHolder.getBean(com.knowledgebase.domain.repository.TemplateRepository.class);
            actualContent = templateRepository.findById(templateId)
                .map(com.knowledgebase.domain.model.Template::getContent)
                .orElse(content);
        }

        // 1. Сохраняем метаданные в БД с временным путем, чтобы получить ID
        Document document = Document.create(title, authorId, spaceId, "pending/" + System.nanoTime());
        if (parentId != null) {
            document.setParentDocumentId(parentId);
        }

        Document savedDocument = documentRepository.save(document);

        // 2. Формируем финальный путь
        String sanitizedSpaceName = spaceRepository.findById(spaceId)
            .map(s -> s.getName().replaceAll("[\\\\/:*?\"<>|\\s]", "-"))
            .orElse(String.valueOf(spaceId));
        String sanitizedTitle = title.replaceAll("[\\\\/:*?\"<>|\\s]", "-");

        String gitPath = String.format("spaces/%s/%s.md", sanitizedSpaceName, sanitizedTitle);
        savedDocument.updateGitFilePath(gitPath);
        
        // 3. Обновляем метаданные с корректным путем
        Document updatedMetadata = documentRepository.save(savedDocument);

        // 4. Сохраняем контент в Git
        contentRepository.saveContent(
            gitPath,
            actualContent,
            "Create document: " + title,
            author.getLogin(),
            author.getEmail()
        );

        return updatedMetadata;
    }

    private void validateHierarchy(Long documentId, Long parentId, Long spaceId) {
        if (parentId != null) {
            Document parent = documentRepository.findById(parentId)
                    .orElseThrow(() -> new DocumentValidationException("Родительский документ не найден"));

            if (!parent.getSpaceId().equals(spaceId)) {
                throw new DocumentValidationException("Родительский документ принадлежит другому пространству");
            }

            if (documentId != null && documentId.equals(parentId)) {
                throw new DocumentValidationException("Документ не может быть родителем самому себе");
            }

            if (documentId != null) {
                List<Long> ancestors = documentRepository.findAncestorIds(documentId);
                if (ancestors.contains(parentId)) {
                    throw new DocumentValidationException("Циклическая зависимость: выбранный родитель является дочерним элементом");
                }
            }
        }
    }

    private void validateTitleUniqueness(String title, Long spaceId, Long parentId, Long currentDocumentId) {
        boolean exists;
        // Если parentId == 0, считаем его NULL для БД (H2/Postgres)
        Long pid = (parentId != null && parentId > 0) ? parentId : null;

        if (pid == null) {
            exists = documentRepository.existsByTitleAndSpaceIdAndNoParent(title, spaceId);
        } else {
            exists = documentRepository.existsByTitleAndSpaceIdAndParentId(title, spaceId, pid);
        }
        
        if (exists) {
            // Если документ уже существует, проверяем, не тот ли это самый документ, который мы обновляем
            Document existingDoc;
            try {
                if (pid == null) {
                    // Нам нужен метод поиска по заголовку, но его нет в интерфейсе. 
                    // Однако, если заголовок совпадает с текущим, валидация не должна срабатывать при обновлении.
                    Document currentDoc = documentRepository.findById(currentDocumentId).orElse(null);
                    if (currentDoc != null && currentDoc.getTitle().equals(title) && currentDoc.getParentDocumentId() == null) {
                        return;
                    }
                } else {
                    Document currentDoc = documentRepository.findById(currentDocumentId).orElse(null);
                    if (currentDoc != null && currentDoc.getTitle().equals(title) && pid.equals(currentDoc.getParentDocumentId())) {
                        return;
                    }
                }
            } catch (Exception e) {
                // Игнорируем ошибки поиска
            }
            throw new DocumentValidationException("Заголовок '" + title + "' уже занят на этом уровне");
        }
    }
    public Document getDocumentById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    public Document getDocumentBySpaceAndTitle(Long spaceId, String title) {
        return documentRepository.findBySpaceIdAndTitle(spaceId, title)
                .orElseThrow(() -> new DocumentNotFoundException(0L));
    }

    /**
     * Возвращает содержимое документа из Git.
     */
    public String getDocumentContent(Document document) {
        return contentRepository.findContentByPath(document.getGitFilePath())
                .orElse("");
    }

    /**
     * Обновляет документ.
     * Изменяет метаданные в БД и создаёт новый коммит в Git при изменении контента.
     */
    @Transactional
    public Document updateDocument(Long id, String title, String content, DocumentStatus status, Long parentId, Long editorId) {
        Document document = getDocumentById(id);
        User editor = userRepository.findById(editorId)
                .orElseThrow(() -> new UserNotFoundException(editorId));

        log.debug("Обновление документа ID {}: title='{}', status={}, parentId={}", id, title, status, parentId);

        validateHierarchy(id, parentId, document.getSpaceId());
        if (title != null) {
            validateTitleUniqueness(title, document.getSpaceId(), parentId, id);
        }

        // Обновляем метаданные в БД
        document.updateMetadata(title, status);
        if (parentId != null) {
            document.setParentDocumentId(parentId);
        }
        Document updatedMetadata = documentRepository.save(document);

        // Обновляем контент в Git, если передан
        if (content != null) {
            String oldPath = document.getGitFilePath();
            String spaceName = spaceRepository.findById(document.getSpaceId())
                .map(s -> s.getName().replaceAll("[\\\\/:*?\"<>|\\s]", "-"))
                .orElse(String.valueOf(document.getSpaceId()));
            String sanitizedTitle = title != null ? title.replaceAll("[\\\\/:*?\"<>|\\s]", "-") : document.getTitle().replaceAll("[\\\\/:*?\"<>|\\s]", "-");
            
            String newPath = String.format("spaces/%s/%s.md", spaceName, sanitizedTitle);
            
            if (!oldPath.equals(newPath)) {
                contentRepository.moveContent(oldPath, newPath, "Rename document to: " + title);
                document.updateGitFilePath(newPath);
                documentRepository.save(document);
            }

            contentRepository.saveContent(
                    newPath,
                    content,
                    "Update document: " + document.getTitle(),
                    "System",
                    "system@knowledgebase.com"
            );
        }

        return updatedMetadata;
    }

    /**
     * Удаляет документ (переводит в статус DELETED и перемещает файл в .archive/).
     */
    @Transactional
    public void deleteDocument(Long id) {
        Document document = getDocumentById(id);
        
        if (document.getStatus() == DocumentStatus.DELETED) {
            log.info("Документ ID {} уже удален", id);
            return;
        }

        log.info("Архивация документа ID {}: title='{}'", id, document.getTitle());

        String oldPath = document.getGitFilePath();
        String newPath = ".archive/" + oldPath;

        // 1. Перемещаем файл в Git
        contentRepository.moveContent(oldPath, newPath, "Archive document: " + document.getTitle());

        // 2. Обновляем метаданные в БД
        document.archive(newPath);
        documentRepository.save(document);
    }

    /**
     * Восстанавливает документ (переводит из статуса DELETED и перемещает файл из .archive/).
     */
    @Transactional
    public void restoreDocument(Long id) {
        Document document = getDocumentById(id);
        
        if (document.getStatus() != DocumentStatus.DELETED) {
            log.info("Документ ID {} не находится в архиве", id);
            return;
        }

        log.info("Восстановление документа ID {}: title='{}'", id, document.getTitle());

        String archivedPath = document.getGitFilePath();
        String originalPath = archivedPath.replace(".archive/", "");

        // 1. Перемещаем файл в Git
        contentRepository.moveContent(archivedPath, originalPath, "Restore document: " + document.getTitle());

        // 2. Обновляем метаданные в БД
        document.restore(originalPath);
        documentRepository.save(document);
    }

    /**
     * Возвращает все документы, к которым у пользователя есть доступ.
     */
    public List<Document> getAllAccessibleDocuments(Long userId, boolean isAdmin, boolean includeDeleted) {
        if (isAdmin) {
            return documentRepository.findAll(includeDeleted);
        }

        // Проверяем роль пользователя
        GlobalRole role = userRepository.findById(userId)
                .map(User::getRole)
                .orElse(GlobalRole.GUEST);

        if (role == GlobalRole.READER || role == GlobalRole.EDITOR) {
            return documentRepository.findAll(includeDeleted);
        }

        // GUEST: только разрешенные
        return documentRepository.findAccessibleByUserId(userId, includeDeleted);
    }

    /**
     * Возвращает список документов в пространстве.
     */
    public List<Document> getDocumentsInSpace(Long spaceId, boolean includeDeleted) {
        if (!spaceRepository.findById(spaceId).isPresent()) {
            throw new SpaceNotFoundException(spaceId);
        }
        return documentRepository.findBySpaceId(spaceId, includeDeleted);
    }

    /**
     * Возвращает иерархическую структуру документов в пространстве.
     */
    public List<DocumentTreeNode> getSpaceDocumentHierarchy(Long spaceId) {

        List<Document> documents = getDocumentsInSpace(spaceId, false);
        
        Map<Long, List<Document>> childrenMap = documents.stream()
                .filter(d -> d.getParentDocumentId() != null)
                .collect(Collectors.groupingBy(Document::getParentDocumentId));
        
        return documents.stream()
                .filter(d -> d.getParentDocumentId() == null)
                .map(d -> buildNode(d, childrenMap))
                .collect(Collectors.toList());
    }

    private DocumentTreeNode buildNode(Document doc, Map<Long, List<Document>> childrenMap) {
        List<DocumentTreeNode> children = childrenMap.getOrDefault(doc.getId(), List.of()).stream()
                .map(child -> buildNode(child, childrenMap))
                .collect(Collectors.toList());
        return new DocumentTreeNode(doc, children);
    }

    public static class DocumentTreeNode {
        private final Document document;
        private final List<DocumentTreeNode> children;

        public DocumentTreeNode(Document document, List<DocumentTreeNode> children) {
            this.document = document;
            this.children = children;
        }

        public Document getDocument() { return document; }
        public List<DocumentTreeNode> getChildren() { return children; }
    }

}
