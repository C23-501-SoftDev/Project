package com.knowledgebase.application.service;

import com.knowledgebase.domain.event.DocumentUpdatedEvent;
import com.knowledgebase.domain.exception.DocumentNotFoundException;
import com.knowledgebase.domain.exception.DocumentValidationException;
import com.knowledgebase.domain.exception.SpaceNotFoundException;
import com.knowledgebase.domain.exception.UserNotFoundException;
import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.DocumentStatus;
import com.knowledgebase.domain.model.DocumentVersion;
import com.knowledgebase.domain.model.GitCommitResult;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.SpacePermissionRepository;
import com.knowledgebase.domain.repository.DocumentContentRepository;
import com.knowledgebase.domain.repository.DocumentRepository;
import com.knowledgebase.domain.repository.DocumentVersionRepository;
import com.knowledgebase.domain.repository.SpaceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.knowledgebase.domain.repository.TemplateRepository;
import com.knowledgebase.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
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
    private final DocumentVersionRepository documentVersionRepository;
    private final SpaceRepository spaceRepository;
    private final SpacePermissionRepository permissionRepository;
    private final TemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final RequirementNumberService requirementNumberService;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;

    private static final int MAX_SEARCH_QUERY_LENGTH = 200;
    private static final int MAX_SEARCH_PAGE_SIZE = 50;
    private static final LocalDateTime SEARCH_MIN_DATE = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime SEARCH_MAX_DATE = LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_999);

    public DocumentService(DocumentRepository documentRepository,
                           DocumentContentRepository contentRepository,
                           DocumentVersionRepository documentVersionRepository,
                           SpaceRepository spaceRepository,
                           SpacePermissionRepository permissionRepository,
                           TemplateRepository templateRepository,
                           UserRepository userRepository,
                           PermissionService permissionService,
                           RequirementNumberService requirementNumberService,
                           ApplicationEventPublisher eventPublisher,
                           AuditService auditService) {
        this.documentRepository = documentRepository;
        this.contentRepository = contentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.spaceRepository = spaceRepository;
        this.permissionRepository = permissionRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.requirementNumberService = requirementNumberService;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
    }

    /**
     * Создаёт новый документ.
     * Сначала сохраняет метаданные в БД, затем контент в Git.
     *
     * @param title    заголовок
     * @param content  содержимое Markdown
     * @param spaceId  ID пространства
     * @param parentId ID родительского документа
     * @param authorId ID автора
     * @param templateId ID шаблона (опционально)
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

        // Контент может отсутствовать в запросе — создаём документ с пустым содержимым
        String actualContent = content != null ? content : "";
        if (templateId != null) {
            actualContent = templateRepository.findById(templateId)
                .map(com.knowledgebase.domain.model.Template::getContent)
                .orElse(actualContent);
            actualContent = requirementNumberService.numberRequirements(actualContent, spaceId, templateId);
        }

        // 1. Сохраняем метаданные в БД с временным путем, чтобы получить ID
        Document document = Document.create(title, authorId, spaceId, "pending/" + System.nanoTime(), templateId);
        if (parentId != null) {
            document.setParentDocumentId(parentId);
        }
        document.setSortOrder(nextSiblingOrder(spaceId, parentId, null));

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

        auditService.record("DOCUMENT_CREATED", AuditService.RESOURCE_DOCUMENT, updatedMetadata.getId(),
                "title='" + title + "', spaceId=" + spaceId);
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
                List<Long> parentAncestors = documentRepository.findAncestorIds(parentId);
                if (parentAncestors.contains(documentId)) {
                    throw new DocumentValidationException("Циклическая зависимость: выбранный родитель является дочерним элементом");
                }
            }

            if (parent.getStatus() == DocumentStatus.DELETED) {
                throw new DocumentValidationException("Удаленный документ нельзя выбрать родителем");
            }
        }
    }

    /**
     * Перемещает документ в другое пространство, меняет его родителя или позицию среди соседей.
     */
    @Transactional
    public Document moveDocument(Long id, Long targetSpaceId, Long targetParentId, Long editorId) {
        return moveDocument(id, targetSpaceId, targetParentId, null, editorId);
    }

    /**
     * @param targetPosition итоговая позиция среди документов с тем же родителем, начиная с нуля;
     *                       {@code null} сохраняет позицию при перемещении в пределах одного уровня
     *                       и добавляет документ в конец при смене уровня
     */
    @Transactional
    public Document moveDocument(Long id, Long targetSpaceId, Long targetParentId,
                                 Integer targetPosition, Long editorId) {
        Document document = getDocumentById(id);
        if (document.getStatus() == DocumentStatus.DELETED) {
            throw new DocumentValidationException("Нельзя перемещать удаленный документ");
        }

        User editor = userRepository.findById(editorId)
                .orElseThrow(() -> new UserNotFoundException(editorId));

        Long sourceSpaceId = document.getSpaceId();
        Long sourceParentId = document.getParentDocumentId();

        // Если целевое пространство не указано, оставляем текущее
        Long finalSpaceId = targetSpaceId != null ? targetSpaceId : sourceSpaceId;

        if (spaceRepository.findById(finalSpaceId).isEmpty()) {
            throw new SpaceNotFoundException(finalSpaceId);
        }

        // Валидация иерархии
        validateHierarchy(id, targetParentId, finalSpaceId);

        // Если родитель указан, проверяем, принадлежит ли он целевому пространству
        if (targetParentId != null) {
            Document targetParent = getDocumentById(targetParentId);
            if (!targetParent.getSpaceId().equals(finalSpaceId)) {
                throw new DocumentValidationException("Родительский документ принадлежит другому пространству");
            }
        }

        // Проверяем уникальность названия на новом уровне
        validateTitleUniqueness(document.getTitle(), finalSpaceId, targetParentId, id);

        boolean spaceChanged = !sourceSpaceId.equals(finalSpaceId);
        boolean parentChanged = !Objects.equals(sourceParentId, targetParentId);
        boolean sameSiblingGroup = !spaceChanged && !parentChanged;

        List<Document> sourceOrder = findActiveSiblings(sourceSpaceId, sourceParentId, null);
        int sourceIndex = indexOfDocument(sourceOrder, id);
        if (sourceIndex < 0) {
            throw new DocumentValidationException("Документ отсутствует среди элементов исходного уровня");
        }

        List<Document> targetOrder = sameSiblingGroup
                ? new ArrayList<>(sourceOrder)
                : findActiveSiblings(finalSpaceId, targetParentId, id);
        targetOrder.removeIf(candidate -> candidate.getId().equals(id));

        int insertionIndex;
        if (targetPosition == null && sameSiblingGroup) {
            insertionIndex = Math.min(sourceIndex, targetOrder.size());
        } else if (targetPosition == null) {
            insertionIndex = targetOrder.size();
        } else {
            insertionIndex = Math.max(0, Math.min(targetPosition, targetOrder.size()));
        }
        targetOrder.add(insertionIndex, document);

        List<Long> currentIds = sourceOrder.stream().map(Document::getId).toList();
        List<Long> targetIds = targetOrder.stream().map(Document::getId).toList();
        boolean orderChanged = !sameSiblingGroup || !currentIds.equals(targetIds);

        if (spaceChanged || parentChanged) {
            document.setSortOrder(insertionIndex);
            if (spaceChanged) {
                // Перемещаем документ и всех его потомков рекурсивно в новое пространство
                moveDocumentAndDescendantsToSpace(document, finalSpaceId, targetParentId, editor);
            } else {
                // Изменился только родитель в том же пространстве
                document.setParentDocumentId(targetParentId);
                documentRepository.save(document);
                
                // Фиксируем изменение в Git, если документ опубликован
                if (document.getStatus() == DocumentStatus.PUBLISHED) {
                    String gitPath = document.getGitFilePath();
                    String content = getDocumentContent(document);
                    String metadataPath = ".metadata/documents/" + document.getId() + ".json";
                    contentRepository.saveDocumentSnapshot(
                            gitPath, gitPath, content, metadataPath,
                            documentMetadataJson(document), "Move document: " + document.getTitle(),
                            editor.getLogin(), editor.getEmail());
                } else {
                    documentRepository.save(document);
                }
            }
        }

        if (!sameSiblingGroup) {
            List<Document> remainingSourceOrder = sourceOrder.stream()
                    .filter(candidate -> !candidate.getId().equals(id))
                    .toList();
            persistSiblingOrder(remainingSourceOrder);
        }
        persistSiblingOrder(targetOrder);

        if (spaceChanged || parentChanged || orderChanged) {
            auditService.record("DOCUMENT_MOVED", AuditService.RESOURCE_DOCUMENT, id,
                    "title='" + document.getTitle() + "', fromSpaceId=" + sourceSpaceId
                            + ", toSpaceId=" + finalSpaceId + ", parentId=" + targetParentId
                            + ", position=" + insertionIndex);

            // Генерируем событие обновления
            eventPublisher.publishEvent(new DocumentUpdatedEvent(
                    document.getId(),
                    document.getTitle(),
                    finalSpaceId,
                    editorId,
                    editor.getLogin()
            ));
        }

        return getDocumentById(id);
    }

    private List<Document> findActiveSiblings(Long spaceId, Long parentId, Long excludedDocumentId) {
        return documentRepository.findBySpaceId(spaceId, false).stream()
                .filter(candidate -> Objects.equals(candidate.getParentDocumentId(), parentId))
                .filter(candidate -> excludedDocumentId == null || !candidate.getId().equals(excludedDocumentId))
                .sorted(documentOrderComparator())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private int nextSiblingOrder(Long spaceId, Long parentId, Long excludedDocumentId) {
        return findActiveSiblings(spaceId, parentId, excludedDocumentId).stream()
                .mapToInt(Document::getSortOrder)
                .max()
                .orElse(-1) + 1;
    }

    private int indexOfDocument(List<Document> documents, Long documentId) {
        for (int index = 0; index < documents.size(); index++) {
            if (documents.get(index).getId().equals(documentId)) {
                return index;
            }
        }
        return -1;
    }

    private void persistSiblingOrder(List<Document> documents) {
        for (int index = 0; index < documents.size(); index++) {
            Document sibling = documents.get(index);
            if (sibling.getSortOrder() != index) {
                sibling.setSortOrder(index);
                documentRepository.save(sibling);
            }
        }
    }

    private Comparator<Document> documentOrderComparator() {
        return Comparator.comparingInt(Document::getSortOrder)
                .thenComparing(Document::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private void moveDocumentAndDescendantsToSpace(Document document, Long targetSpaceId, Long targetParentId, User editor) {
        String targetSpaceName = spaceRepository.findById(targetSpaceId)
                .map(s -> s.getName().replaceAll("[\\\\/:*?\"<>|\\s]", "-"))
                .orElse(String.valueOf(targetSpaceId));

        // Сначала соберем все descendants рекурсивно
        List<Document> allDocs = documentRepository.findBySpaceId(document.getSpaceId(), true);
        java.util.Map<Long, List<Document>> childrenMap = allDocs.stream()
                .filter(d -> d.getParentDocumentId() != null)
                .collect(Collectors.groupingBy(Document::getParentDocumentId));

        // Будем обходить дерево в ширину или глубину, обновляя spaceId, parentDocumentId и gitFilePath
        updateSpaceAndPathsRecursive(document, targetSpaceId, targetParentId, targetSpaceName, childrenMap, editor);
    }

    private void updateSpaceAndPathsRecursive(Document doc, Long targetSpaceId, Long targetParentId, String targetSpaceName,
                                             java.util.Map<Long, List<Document>> childrenMap, User editor) {
        String oldPath = doc.getGitFilePath();
        String content = getDocumentContent(doc);

        String sanitizedTitle = doc.getTitle().replaceAll("[\\\\/:*?\"<>|\\s]", "-");
        String newPath = String.format("spaces/%s/%s.md", targetSpaceName, sanitizedTitle);

        // Обновляем spaceId и parentId в доменной модели
        doc.moveToSpace(targetSpaceId);
        doc.setParentDocumentId(targetParentId);
        doc.updateGitFilePath(newPath);

        // Сохраняем в Git / переносим контент
        if (doc.getStatus() == DocumentStatus.PUBLISHED) {
            String metadataPath = ".metadata/documents/" + doc.getId() + ".json";
            contentRepository.saveDocumentSnapshot(
                    oldPath, newPath, content, metadataPath,
                    documentMetadataJson(doc), "Move document: " + doc.getTitle() + " to space " + targetSpaceName,
                    editor.getLogin(), editor.getEmail());
        } else {
            contentRepository.moveContent(oldPath, newPath, "Move document: " + doc.getTitle() + " to space " + targetSpaceName);
        }

        documentRepository.save(doc);

        // Рекурсивно обрабатываем детей
        List<Document> children = childrenMap.getOrDefault(doc.getId(), java.util.Collections.emptyList());
        for (Document child : children) {
            // Для ребенка родителем остается текущий документ doc.getId(), а targetSpaceId передается дальше
            updateSpaceAndPathsRecursive(child, targetSpaceId, doc.getId(), targetSpaceName, childrenMap, editor);
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
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        return document;
    }

    /**
     * Проверяет, имеет ли пользователь право просматривать конкретный документ (с учетом статуса Draft/Published/Deleted).
     */
    public boolean canViewDocument(Long userId, boolean isAdmin, Document document) {
        if (document == null) {
            return false;
        }
        if (document.getStatus() == DocumentStatus.DELETED) {
            // Удаленные документы обрабатываются отдельно (например, в корзине / с правами пространства)
            return true;
        }
        if (document.getStatus() == DocumentStatus.PUBLISHED) {
            return permissionService != null ? permissionService.canRead(userId, isAdmin, document.getSpaceId()) : true;
        }
        if (document.getStatus() == DocumentStatus.DRAFT) {
            // Черновик доступен только автору или администратору
            if (isAdmin) {
                return true;
            }
            return userId != null && userId.equals(document.getAuthorId());
        }
        return false;
    }

    /**
     * Публикация документа (перевод из Draft в Published).
     * Публиковать может только автор документа или администратор.
     */
    @Transactional
    public Document publishDocument(Long id, Long userId, boolean isAdmin) {
        Document document = getDocumentById(id);

        if (document.getStatus() == DocumentStatus.DELETED) {
            throw new DocumentValidationException("Нельзя опубликовать удаленный документ");
        }
        if (document.getStatus() == DocumentStatus.PUBLISHED) {
            return document; // Уже опубликован
        }

        // Проверка прав: публиковать может только автор или администратор
        boolean isAuthor = userId != null && userId.equals(document.getAuthorId());
        if (!isAdmin && !isAuthor) {
            throw new com.knowledgebase.domain.exception.AccessDeniedException("Только автор или администратор могут опубликовать документ");
        }

        document.updateMetadata(null, DocumentStatus.PUBLISHED);
        Document saved = documentRepository.save(document);

        auditService.record("DOCUMENT_PUBLISHED", AuditService.RESOURCE_DOCUMENT, saved.getId(),
                "title='" + saved.getTitle() + "', spaceId=" + saved.getSpaceId());
        return saved;
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
     * Обновляет документ (сохранена перегрузка для обратной совместимости тестов).
     */
    @Transactional
    public Document updateDocument(Long id, String title, String content, DocumentStatus status, Long parentId, Long editorId) {
        if (status != null) {
            throw new DocumentValidationException("Нельзя менять статус документа через обычное редактирование");
        }
        return updateDocument(id, title, content, parentId, editorId);
    }
    /**
     * Обновляет документ.
     * Изменяет метаданные в БД и создаёт новый коммит в Git при изменении контента.
     */
    @Transactional
    public Document updateDocument(Long id, String title, String content, Long parentId, Long editorId) {
        Document document = getDocumentById(id);
        if (document.getStatus() == DocumentStatus.DELETED) {
            throw new DocumentValidationException("Нельзя изменять удаленный документ");
        }

        User editor = userRepository.findById(editorId)
                .orElseThrow(() -> new UserNotFoundException(editorId));

        String oldTitle = document.getTitle();
        DocumentStatus oldStatus = document.getStatus();
        Long oldParentId = document.getParentDocumentId();
        String oldPath = document.getGitFilePath();
        String existingContent = contentRepository.findContentByPath(oldPath).orElse("");

        log.debug("Обновление документа ID {}: title='{}', status={}, parentId={}",
                id, title, document.getStatus(), parentId);

        validateHierarchy(id, parentId, document.getSpaceId());
        if (title != null) {
            validateTitleUniqueness(title, document.getSpaceId(), parentId, id);
        }

        // Обновляем метаданные в БД (без изменения статуса через обычный PUT)
        document.updateMetadata(title, document.getStatus());
        if (parentId != null) {
            document.setParentDocumentId(parentId);
        }
        String newPath = oldPath;
        String actualContent = content != null ? content : existingContent;
        if (content != null || title != null) {
            String spaceName = spaceRepository.findById(document.getSpaceId())
                .map(s -> s.getName().replaceAll("[\\\\/:*?\"<>|\\s]", "-"))
                .orElse(String.valueOf(document.getSpaceId()));
            String sanitizedTitle = title != null ? title.replaceAll("[\\\\/:*?\"<>|\\s]", "-") : document.getTitle().replaceAll("[\\\\/:*?\"<>|\\s]", "-");
            newPath = String.format("spaces/%s/%s.md", spaceName, sanitizedTitle);
            if (content != null && document.getTemplateId() != null) {
                actualContent = requirementNumberService.numberMissingRequirements(
                        actualContent,
                        document.getSpaceId(),
                        document.getTemplateId()
                );
            }

        }

        boolean metadataChanged = !java.util.Objects.equals(oldTitle, document.getTitle())
                || oldStatus != document.getStatus()
                || !java.util.Objects.equals(oldParentId, document.getParentDocumentId());
        boolean contentChanged = !java.util.Objects.equals(existingContent, actualContent);
        boolean pathChanged = !oldPath.equals(newPath);
        boolean changed = metadataChanged || contentChanged || pathChanged;
        String commitMessage = "Update document: " + document.getTitle();

        if (changed && document.getStatus() == DocumentStatus.PUBLISHED) {
            String metadataPath = ".metadata/documents/" + document.getId() + ".json";
            GitCommitResult commit = contentRepository.saveDocumentSnapshot(
                    oldPath, newPath, actualContent, metadataPath,
                    documentMetadataJson(document), commitMessage, editor.getLogin(), editor.getEmail());
            if (commit == null) {
                throw new IllegalStateException("Измененный документ не был добавлен в Git-коммит");
            }
            if (pathChanged) {
                document.updateGitFilePath(newPath);
            }
            Document updatedMetadata = documentRepository.save(document);
            try {
                documentVersionRepository.save(DocumentVersion.create(updatedMetadata.getId(), commit.hash(),
                        editor.getId(), commitMessage, commit.committedAt()));
            } catch (RuntimeException e) {
                log.error("Git-коммит {} создан, но метаданные версии документа {} не сохранены",
                        commit.hash(), id, e);
                throw e;
            }
        } else {
            if (content != null) {
                if (pathChanged) {
                    contentRepository.moveContent(oldPath, newPath, "Rename document to: " + document.getTitle());
                    document.updateGitFilePath(newPath);
                }
                contentRepository.saveContent(newPath, actualContent, commitMessage, editor.getLogin(), editor.getEmail());
            }
            documentRepository.save(document);
        }

        Document updatedMetadata = document;

        // Уведомляем участников пространства об изменении документа (US4.3.1).
        // Слушатель сработает после фиксации текущей транзакции (AFTER_COMMIT).
        eventPublisher.publishEvent(new DocumentUpdatedEvent(
                updatedMetadata.getId(),
                updatedMetadata.getTitle(),
                document.getSpaceId(),
                editorId,
                editor.getLogin()
        ));

        auditService.record("DOCUMENT_UPDATED", AuditService.RESOURCE_DOCUMENT, updatedMetadata.getId(),
                "title='" + updatedMetadata.getTitle() + "', spaceId=" + document.getSpaceId());
        return updatedMetadata;
    }

    private String documentMetadataJson(Document document) {
        return "{\n"
                + "  \"documentId\": " + document.getId() + ",\n"
                + "  \"title\": \"" + jsonEscape(document.getTitle()) + "\",\n"
                + "  \"status\": \"" + document.getStatus().name() + "\",\n"
                + "  \"parentDocumentId\": "
                + (document.getParentDocumentId() == null ? "null" : document.getParentDocumentId()) + ",\n"
                + "  \"sortOrder\": " + document.getSortOrder() + "\n"
                + "}\n";
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    /**
     * Удаляет документ (переводит в статус DELETED и перемещает файл в .archive/).
     * Дочерние документы привязываются к родителю удаляемого документа.
     *
     * @param id ID документа
     * @param reparentChildren если true, дочерние документы перепривязываются к родителю (используется при обычном удалении)
     */
    @Transactional
    public void deleteDocument(Long id, boolean reparentChildren) {
        Document document = getDocumentById(id);
        
        if (document.getStatus() == DocumentStatus.DELETED) {
            log.info("Документ ID {} уже удален", id);
            return;
        }

        Long grandparentOrParentId = document.getParentDocumentId();

        if (reparentChildren) {
            List<Document> children = documentRepository.findBySpaceId(document.getSpaceId(), true).stream()
                    .filter(d -> id.equals(d.getParentDocumentId()))
                    .toList();

            for (Document child : children) {
                if (child.getPreviousParentId() == null) {
                    child.setPreviousParentId(id);
                }
                child.setParentDocumentId(grandparentOrParentId);
                documentRepository.save(child);
                log.debug(
                    "Дочерний документ ID {} переприведен к дедушке ID {}, сохранен previousParentId={}",
                    child.getId(), grandparentOrParentId, id
                );
            }
        }

        log.info("Архивация документа ID {}: title='{}'", id, document.getTitle());

        String oldPath = document.getGitFilePath();
        // Если документ уже имеет путь в архиве, избегаем двойной архивации
        String newPath = oldPath.startsWith(".archive/") ? oldPath : ".archive/" + oldPath;

        // 1. Перемещаем файл в Git, если он существует и еще не в архиве
        if (!oldPath.startsWith(".archive/")) {
            try {
                contentRepository.moveContent(oldPath, newPath, "Archive document: " + document.getTitle());
            } catch (Exception e) {
                log.warn("Не удалось архивировать файл документа {}: {}", id, e.getMessage());
            }
        }

        // 2. Обновляем метаданные в БД
        document.archive(newPath, document.getParentDocumentId());
        document.setParentDocumentId(null);
        documentRepository.save(document);
        auditService.record("DOCUMENT_DELETED", AuditService.RESOURCE_DOCUMENT, id,
                "title='" + document.getTitle() + "'");
    }

    /**
     * Удаляет документ с перепривязкой дочерних элементов.
     */
    @Transactional
    public void deleteDocument(Long id) {
        deleteDocument(id, true);
    }

    /**
     * Удаляет документ навсегда (hard-delete).
     */
    @Transactional
    public void hardDeleteDocument(Long id) {
        if (documentRepository.hasChildren(id)) {
            throw new DocumentValidationException("Нельзя удалить документ, у которого есть дочерние документы");
        }
        Document document = getDocumentById(id);
        log.info("Полное удаление документа ID {}: title='{}'", id, document.getTitle());

        // 1. Удаляем из БД
        documentRepository.deleteById(id);

        // 2. Удаляем файл из Git
        contentRepository.deleteContent(document.getGitFilePath(), "Hard delete document: " + document.getTitle());

        auditService.record("DOCUMENT_HARD_DELETED", AuditService.RESOURCE_DOCUMENT, id,
                "title='" + document.getTitle() + "'");
    }

    /**
     * Восстанавливает документ (переводит из статуса DELETED и перемещает файл из .archive/).
     */
    @Transactional
    public void restoreDocument(Long id, boolean keepHierarchy) {
        Document document = getDocumentById(id);
        
        if (document.getStatus() != DocumentStatus.DELETED) {
            log.info("Документ ID {} не находится в архиве", id);
            return;
        }

        // Проверяем статус пространства (включая удалённые — для корректного 409)
        Space space = spaceRepository.findByIdIncludingDeleted(document.getSpaceId())
                .orElseThrow(() -> new SpaceNotFoundException(document.getSpaceId()));
        
        if (space.isDeleted()) {
            throw new com.knowledgebase.domain.exception.ConflictException(
                "Нельзя восстановить документ в удаленном (неактивном пространстве)");
        }

        Long targetParentId = document.getPreviousParentId();
        if (!keepHierarchy && targetParentId != null) {
            Document parent = documentRepository.findById(targetParentId).orElse(null);
            if (parent == null || parent.getStatus() == DocumentStatus.DELETED) {
                targetParentId = findFirstActiveAncestor(targetParentId);
            }
        }

        log.info("Восстановление документа ID {}: title='{}'", id, document.getTitle());

        String archivedPath = document.getGitFilePath();
        String originalPath = archivedPath.startsWith(".archive/") ? archivedPath.substring(".archive/".length()) : archivedPath;

        // 1. Перемещаем файл из Git архива обратно, если он там
        if (archivedPath.startsWith(".archive/")) {
            try {
                contentRepository.moveContent(archivedPath, originalPath, "Restore document: " + document.getTitle());
            } catch (Exception e) {
                log.warn("Не удалось восстановить файл документа {} из архива: {}", id, e.getMessage());
            }
        }

        // 2. Обновляем метаданные в БД
        document.restore(originalPath, targetParentId);
        // Сброс флага восстановления
        document.markAsDeletedWithSpace(false);
        documentRepository.save(document);

        // Возвращаем обратно детей, которые были временно переподчинены при удалении этого документа
        List<Document> potentialChildren = documentRepository.findBySpaceId(document.getSpaceId(), true).stream()
                .filter(d -> id.equals(d.getPreviousParentId()))
                .toList();

        for (Document child : potentialChildren) {
            child.setParentDocumentId(id);
            child.setPreviousParentId(null);
            documentRepository.save(child);
            log.debug("Дочерний документ ID {} возвращен под восстановленного родителя ID {}", child.getId(), id);
        }

        documentRepository.flush();

        auditService.record("DOCUMENT_RESTORED", AuditService.RESOURCE_DOCUMENT, id,
                "title='" + document.getTitle() + "'");
    }
    /**
     * Восстанавливает документ с автоматическим поиском живого предка.
     */
    @Transactional
    public void restoreDocument(Long id) {
        restoreDocument(id, false);
    }

    private Long findFirstActiveAncestor(Long parentId) {
        if (parentId == null) {
            return null;
        }
        Document parent = documentRepository.findById(parentId).orElse(null);
        if (parent == null) {
            return null;
        }
        if (parent.getStatus() != DocumentStatus.DELETED) {
            return parent.getId();
        }
        return findFirstActiveAncestor(parent.getParentDocumentId());
    }

    /**
     * Возвращает все документы, к которым у пользователя есть доступ.
     */
    public List<Document> getAllAccessibleDocuments(Long userId, boolean isAdmin, boolean includeDeleted) {
        if (userId == null) {
            return java.util.Collections.emptyList();
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return java.util.Collections.emptyList();
        }

        GlobalRole role = user.getRole();

        if (isAdmin && role != GlobalRole.GUEST) {
            return documentRepository.findAll(includeDeleted);
        }

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
        if (!spaceRepository.findByIdIncludingDeleted(spaceId).isPresent()) {
            throw new SpaceNotFoundException(spaceId);
        }
        List<Document> documents = documentRepository.findBySpaceId(spaceId, includeDeleted);
        
        if (!includeDeleted) {
            return documents.stream()
                    .filter(d -> d.getStatus() != DocumentStatus.DELETED)
                    .collect(Collectors.toList());
        }
        return documents;
    }

    /**
     * Получает список документов в пространстве с пагинацией.
     */
    public List<Document> getDocumentsInSpacePaged(Long spaceId, Long authorId, boolean includeDeleted, int page, int size) {
        if (!spaceRepository.findByIdIncludingDeleted(spaceId).isPresent()) {
            throw new SpaceNotFoundException(spaceId);
        }
        if (authorId != null) {
            return documentRepository.findBySpaceIdAndAuthorIdPaged(spaceId, authorId, includeDeleted, page, size);
        }
        return documentRepository.findBySpaceIdPaged(spaceId, includeDeleted, page, size);
    }

    /**
     * Получить список удаленных документов для административной корзины.
     */
    public List<Document> getRecycleBinDocuments() {
        return documentRepository.findAll(true).stream()
                .filter(d -> d.getStatus() == DocumentStatus.DELETED)
                .collect(Collectors.toList());
    }

    /**
     * Получить список удаленных документов для административной корзины с фильтрацией.
     */
    public List<Document> getRecycleBinDocuments(Long spaceId, Long authorId) {
        return documentRepository.findDeletedDocuments(spaceId, authorId);
    }
    /**
     * Получить список пространств, в которых есть удаленные документы.
     */
    public List<Space> getRecycleBinSpaces() {
        Set<Long> spaceIds = documentRepository.findAll(true).stream()
                .filter(d -> d.getStatus() == DocumentStatus.DELETED)
                .map(Document::getSpaceId)
                .collect(Collectors.toSet());

        if (spaceIds.isEmpty()) {
            return Collections.emptyList();
        }
        return spaceRepository.findAllByIdIn(spaceIds);
    }

    /**
     * Получить список авторов, у которых есть удаленные документы.
     */
    public List<User> getRecycleBinAuthors() {
        Set<Long> authorIds = documentRepository.findAll(true).stream()
                .filter(d -> d.getStatus() == DocumentStatus.DELETED)
                .map(Document::getAuthorId)
                .collect(Collectors.toSet());

        if (authorIds.isEmpty()) {
            return Collections.emptyList();
        }
        return authorIds.stream()
                .map(id -> userRepository.findById(id).orElse(null))
                .filter(u -> u != null)
                .collect(Collectors.toList());
    }

    /**
     * Возвращает список авторов для доступных пространств.
     */
    public List<User> findDistinctAuthorsByAccessibleSpaces(Long userId) {
        return documentRepository.findDistinctAuthorsByAccessibleSpaces(userId);
    }
    /**
     * Класс узла дерева документов для боковой панели.
     */
    public static class DocumentTreeNode {
        private final Document document;
        private final List<DocumentTreeNode> children;

        public DocumentTreeNode(Document document, List<DocumentTreeNode> children) {
            this.document = document;
            this.children = children;
        }

        public Document getDocument() {
            return document;
        }

        public List<DocumentTreeNode> getChildren() {
            return children;
        }
    }

    /**
     * Возвращает иерархию документов для списка пространств.
     */
    public Map<Long, List<DocumentTreeNode>> getHierarchiesForSpaces(List<Long> spaceIds) {
        if (spaceIds == null || spaceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Document> docs = documentRepository.findBySpaceIdIn(spaceIds, false);
        Map<Long, List<Document>> bySpace = docs.stream().collect(Collectors.groupingBy(Document::getSpaceId));

        Map<Long, List<DocumentTreeNode>> result = new java.util.HashMap<>();
        for (Map.Entry<Long, List<Document>> entry : bySpace.entrySet()) {
            result.put(entry.getKey(), buildTree(entry.getValue()));
        }
        return result;
    }

    private List<DocumentTreeNode> buildTree(List<Document> documents) {
        Map<Long, DocumentTreeNode> nodeMap = new java.util.LinkedHashMap<>();
        List<DocumentTreeNode> roots = new ArrayList<>();
        List<Document> orderedDocuments = documents.stream()
                .sorted(documentOrderComparator())
                .toList();

        for (Document doc : orderedDocuments) {
            nodeMap.put(doc.getId(), new DocumentTreeNode(doc, new ArrayList<>()));
        }

        for (Document doc : orderedDocuments) {
            DocumentTreeNode node = nodeMap.get(doc.getId());
            Long parentId = doc.getParentDocumentId();
            if (parentId != null && nodeMap.containsKey(parentId)) {
                nodeMap.get(parentId).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    /**
     * Ищет документы по заголовку с учётом прав доступа.
     */
    public Page<Document> searchDocumentsByTitle(String query,
                                                 LocalDate dateFrom,
                                                 LocalDate dateTo,
                                                 Long userId,
                                                 boolean isAdmin,
                                                 int page,
                                                 int size) {
        validateSearchParameters(query, dateFrom, dateTo, page, size);

        Pageable pageable = PageRequest.of(page, size);
        String normalizedQuery = query != null ? query.trim() : "";
        LocalDateTime effectiveFrom = dateFrom != null ? dateFrom.atStartOfDay() : SEARCH_MIN_DATE;
        LocalDateTime effectiveTo = dateTo != null ? dateTo.atTime(LocalTime.MAX) : SEARCH_MAX_DATE;

        if (userId == null) {
            return Page.empty(pageable);
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Page.empty(pageable);
        }

        GlobalRole role = user.getRole();
        if (isAdmin && role != GlobalRole.GUEST) {
            return documentRepository.searchByTitle(normalizedQuery, effectiveFrom, effectiveTo, pageable);
        }

        if (role == GlobalRole.READER || role == GlobalRole.EDITOR) {
            return documentRepository.searchByTitle(normalizedQuery, effectiveFrom, effectiveTo, pageable);
        }

        Set<Long> accessibleSpaceIds = permissionRepository.findByUserId(userId).stream()
                .map(SpacePermission::getSpaceId)
                .collect(Collectors.toSet());

        if (accessibleSpaceIds.isEmpty()) {
            return Page.empty(pageable);
        }

        return documentRepository.searchByTitleInSpaces(accessibleSpaceIds, normalizedQuery, effectiveFrom, effectiveTo, pageable);
    }

    private void validateSearchParameters(String query, LocalDate dateFrom, LocalDate dateTo, int page, int size) {
        String normalizedQuery = query != null ? query.trim() : "";
        if (normalizedQuery.isBlank() && dateFrom == null && dateTo == null) {
            throw new IllegalArgumentException("Укажите поисковую строку или хотя бы одну дату");
        }
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("Дата начала не может быть позже даты окончания");
        }
        if (page < 0) {
            throw new IllegalArgumentException("Номер страницы не может быть отрицательным");
        }
        if (size <= 0 || size > MAX_SEARCH_PAGE_SIZE) {
            throw new IllegalArgumentException("Размер страницы должен быть от 1 до " + MAX_SEARCH_PAGE_SIZE);
        }
        if (normalizedQuery.length() > MAX_SEARCH_QUERY_LENGTH) {
            throw new IllegalArgumentException("Поисковый запрос слишком длинный (максимум " + MAX_SEARCH_QUERY_LENGTH + " символов)");
        }
    }

}
