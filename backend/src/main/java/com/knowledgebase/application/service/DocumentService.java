package com.knowledgebase.application.service;

import com.knowledgebase.domain.exception.DocumentNotFoundException;
import com.knowledgebase.domain.exception.DocumentValidationException;
import com.knowledgebase.domain.exception.SpaceNotFoundException;
import com.knowledgebase.domain.exception.UserNotFoundException;
import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.DocumentStatus;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.SpacePermissionRepository;
import com.knowledgebase.domain.repository.DocumentContentRepository;
import com.knowledgebase.domain.repository.DocumentRepository;
import com.knowledgebase.domain.repository.SpaceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.knowledgebase.domain.repository.TemplateRepository;
import com.knowledgebase.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import java.util.ArrayList;
import java.util.Collections;
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
    private final SpacePermissionRepository permissionRepository;
    private final TemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final RequirementNumberService requirementNumberService;

    private static final int MAX_SEARCH_QUERY_LENGTH = 200;
    private static final int MAX_SEARCH_PAGE_SIZE = 50;
    private static final LocalDateTime SEARCH_MIN_DATE = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime SEARCH_MAX_DATE = LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_999);

    public DocumentService(DocumentRepository documentRepository,
                           DocumentContentRepository contentRepository,
                           SpaceRepository spaceRepository,
                           SpacePermissionRepository permissionRepository,
                           TemplateRepository templateRepository,
                           UserRepository userRepository,
                           RequirementNumberService requirementNumberService) {
        this.documentRepository = documentRepository;
        this.contentRepository = contentRepository;
        this.spaceRepository = spaceRepository;
        this.permissionRepository = permissionRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.requirementNumberService = requirementNumberService;
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

        if (reparentChildren) {
            // Перепривязываем дочерние документы к родителю текущего документа
            Long newParentId = document.getParentDocumentId();
            List<Document> children = documentRepository.findBySpaceId(document.getSpaceId(), true).stream()
                    .filter(d -> id.equals(d.getParentDocumentId()))
                    .toList();
            
            for (Document child : children) {
                child.setParentDocumentId(newParentId);
                documentRepository.save(child);
                log.debug("Дочерний документ ID {} перепривязан к новому родителю ID {}", child.getId(), newParentId);
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
        document.archive(newPath);
        documentRepository.save(document);
    }

    /**
     * Удаляет документ с перепривязкой дочерних документов.
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
    }


    /**
     * Восстанавливает документ (переводит из статуса DELETED и перемещает файл из .archive/).
     *
     * @param id ID документа
     * @param keepHierarchy если true, сохраняет текущего родителя (используется при восстановлении пространства)
     */
    @Transactional
    public void restoreDocument(Long id, boolean keepHierarchy) {
        Document document = getDocumentById(id);
        
        if (document.getStatus() != DocumentStatus.DELETED) {
            log.info("Документ ID {} не находится в архиве", id);
            return;
        }

        // Проверяем статус пространства
        Space space = spaceRepository.findById(document.getSpaceId())
                .orElseThrow(() -> new SpaceNotFoundException(document.getSpaceId()));
        
        if (space.isDeleted()) {
            throw new com.knowledgebase.domain.exception.ConflictException(
                "Нельзя восстановить документ в удаленном (неактивном) пространстве");
        }

        // Проверяем родителя при восстановлении
        if (!keepHierarchy && document.getParentDocumentId() != null) {
            Document parent = documentRepository.findById(document.getParentDocumentId()).orElse(null);
            if (parent == null || parent.getStatus() == DocumentStatus.DELETED) {
                // Ищем первого неудаленного предка
                Long newParentId = findFirstActiveAncestor(document.getParentDocumentId());
                document.setParentDocumentId(newParentId);
                log.info("Родитель документа ID {} удален. Установлен новый предок ID {}", id, newParentId);
            }
        }
        
        // При восстановлении пространства (keepHierarchy=true) мы НЕ должны менять родителя,
        // но текущий код в deleteDocument перепривязывает детей к дедушке!
        // Это и есть причина потери иерархии при удалении.

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
        document.restore(originalPath);
        // Сброс флага восстановления
        document.markAsDeletedWithSpace(false);
        documentRepository.save(document);
        documentRepository.flush();

        // Проверяем контент после восстановления
        if (contentRepository.findContentByPath(originalPath).orElse("").isEmpty()) {
            log.error("После восстановления документ пуст: {}. Попытка восстановить из .archive/", originalPath);
            // Если документ пуст, пробуем принудительно восстановить из архивной версии, 
            // так как moveContent мог переместить файл, но контент не обновился.
            // Пытаемся найти контент в исходной архивной локации (если файл там еще остался) 
            // или в истории git, но пока пробуем просто прочитать архив.
            try {
                String archivedContent = contentRepository.findContentByPath(archivedPath).orElse("");
                if (!archivedContent.isEmpty()) {
                    contentRepository.saveContent(originalPath, archivedContent, "Restore content from archive: " + document.getTitle(), "System", "system@knowledgebase.com");
                    log.info("Контент успешно восстановлен из архива для документа: {}", originalPath);
                }
            } catch (Exception e) {
                log.error("Не удалось восстановить контент из архива", e);
            }
        }
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
        if (!spaceRepository.findById(spaceId).isPresent()) {
            throw new SpaceNotFoundException(spaceId);
        }
        // Получаем документы из репозитория
        List<Document> documents = documentRepository.findBySpaceId(spaceId, includeDeleted);
        
        // Дополнительно фильтруем удаленные документы, если они не должны быть включены
        if (!includeDeleted) {
            return documents.stream()
                    .filter(d -> d.getStatus() != DocumentStatus.DELETED)
                    .collect(Collectors.toList());
        }
        
        return documents;
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
        if (!normalizedQuery.isBlank() && normalizedQuery.length() > MAX_SEARCH_QUERY_LENGTH) {
            throw new IllegalArgumentException("Поисковая строка не может превышать " + MAX_SEARCH_QUERY_LENGTH + " символов");
        }
        if (page < 0) {
            throw new IllegalArgumentException("Номер страницы не может быть отрицательным");
        }
        if (size < 1 || size > MAX_SEARCH_PAGE_SIZE) {
            throw new IllegalArgumentException("Размер страницы должен быть от 1 до " + MAX_SEARCH_PAGE_SIZE);
        }
    }

    /**
     * Возвращает иерархическую структуру документов в пространстве.
     */
    public List<DocumentTreeNode> getSpaceDocumentHierarchy(Long spaceId) {
        List<Document> documents = getDocumentsInSpace(spaceId, false);
        return buildHierarchy(documents);
    }

    /**
     * Возвращает иерархии документов для нескольких пространств одним запросом к БД.
     * Используется в PageController для устранения N+1.
     */
    public Map<Long, List<DocumentTreeNode>> getHierarchiesForSpaces(List<Long> spaceIds) {
        if (spaceIds == null || spaceIds.isEmpty()) {
            return Map.of();
        }
        List<Document> allDocuments = documentRepository.findBySpaceIdIn(spaceIds, false);

        Map<Long, List<Document>> bySpace = allDocuments.stream()
                .collect(Collectors.groupingBy(Document::getSpaceId));

        return spaceIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> buildHierarchy(bySpace.getOrDefault(id, List.of()))
                ));
    }

    private List<DocumentTreeNode> buildHierarchy(List<Document> documents) {
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

    /**
     * Возвращает список документов в пространстве (возможно, с фильтрацией по автору) с пагинацией на уровне БД.
     */
    public List<Document> getDocumentsInSpacePaged(Long spaceId, Long authorId, boolean includeDeleted, int page, int size) {
        if (!spaceRepository.findById(spaceId).isPresent()) {
            throw new SpaceNotFoundException(spaceId);
        }
        if (authorId != null) {
            return documentRepository.findBySpaceIdAndAuthorIdPaged(spaceId, authorId, includeDeleted, page, size);
        }
        return documentRepository.findBySpaceIdPaged(spaceId, includeDeleted, page, size);
    }

    public long countDocumentsInSpace(Long spaceId, Long authorId, boolean includeDeleted) {
        if (authorId != null) {
            return documentRepository.countBySpaceIdAndAuthorId(spaceId, authorId, includeDeleted);
        }
        return documentRepository.countBySpaceId(spaceId, includeDeleted);
    }

    /**
     * Возвращает страницу документов с комбинированными фильтрами (логика И).
     * Фильтры по пространству и статусу применяются одновременно.
     */
    public DocumentPage listDocuments(Long spaceId,
                                      String statusParam,
                                      boolean includeDeleted,
                                      int page,
                                      int size,
                                      Long userId,
                                      boolean isAdmin) {
        DocumentStatus statusFilter = parseStatusFilter(statusParam);
        Pageable pageable = PageRequest.of(page, size);

        if (spaceId != null) {
            if (!spaceRepository.findById(spaceId).isPresent()) {
                throw new SpaceNotFoundException(spaceId);
            }
            if (statusFilter != null) {
                Page<Document> result = documentRepository.findBySpaceIdAndStatusPaged(spaceId, statusFilter, pageable);
                return toDocumentPage(result, size);
            }
            List<Document> content = documentRepository.findBySpaceIdPaged(spaceId, includeDeleted, page, size);
            long totalElements = documentRepository.countBySpaceId(spaceId, includeDeleted);
            return toDocumentPage(content, totalElements, size);
        }

        Set<Long> accessibleSpaceIds = resolveAccessibleSpaceIds(userId, isAdmin);
        if (accessibleSpaceIds != null && accessibleSpaceIds.isEmpty()) {
            return DocumentPage.empty();
        }

        if (statusFilter != null) {
            Page<Document> result = accessibleSpaceIds == null
                    ? documentRepository.findByStatusPaged(statusFilter, pageable)
                    : documentRepository.findBySpaceIdsAndStatusPaged(accessibleSpaceIds, statusFilter, pageable);
            return toDocumentPage(result, size);
        }

        List<Document> all = getAllAccessibleDocuments(userId, isAdmin, includeDeleted);
        long totalElements = all.size();
        int from = Math.min(page * size, (int) totalElements);
        int to = Math.min(from + size, (int) totalElements);
        List<Document> content = from >= to ? Collections.emptyList() : all.subList(from, to);
        return toDocumentPage(content, totalElements, size);
    }

    public static DocumentStatus parseStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim();
        try {
            return DocumentStatus.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return DocumentStatus.fromDbValue(normalized);
        }
    }

    /**
     * @return null — доступ ко всем пространствам; пустой набор — нет доступа; иначе ограниченный набор ID.
     */
    private Set<Long> resolveAccessibleSpaceIds(Long userId, boolean isAdmin) {
        if (userId == null) {
            return Collections.emptySet();
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Collections.emptySet();
        }

        GlobalRole role = user.getRole();
        if (isAdmin && role != GlobalRole.GUEST) {
            return null;
        }
        if (role == GlobalRole.READER || role == GlobalRole.EDITOR) {
            return null;
        }

        return permissionRepository.findByUserId(userId).stream()
                .map(SpacePermission::getSpaceId)
                .collect(Collectors.toSet());
    }

    private DocumentPage toDocumentPage(Page<Document> page, int size) {
        return toDocumentPage(page.getContent(), page.getTotalElements(), size);
    }

    private DocumentPage toDocumentPage(List<Document> content, long totalElements, int size) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new DocumentPage(content, totalElements, totalPages);
    }

    public record DocumentPage(List<Document> content, long totalElements, int totalPages) {
        public static DocumentPage empty() {
            return new DocumentPage(Collections.emptyList(), 0, 0);
        }
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
    public List<User> findDistinctAuthorsByAccessibleSpaces(Long userId) {
        return documentRepository.findDistinctAuthorsByAccessibleSpaces(userId);
    }
}

