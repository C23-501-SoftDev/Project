package com.knowledgebase.application.service;

import com.knowledgebase.domain.exception.DocumentNotFoundException;
import com.knowledgebase.domain.exception.SpaceNotFoundException;
import com.knowledgebase.domain.exception.UserNotFoundException;
import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.DocumentStatus;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.DocumentContentRepository;
import com.knowledgebase.domain.repository.DocumentRepository;
import com.knowledgebase.domain.repository.SpaceRepository;
import com.knowledgebase.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    @Transactional
    public Document createDocument(String title, String content, Long spaceId, Long authorId) {
        log.debug("Создание документа: title='{}', spaceId={}, authorId={}", title, spaceId, authorId);

        if (!spaceRepository.findById(spaceId).isPresent()) {
            throw new SpaceNotFoundException(spaceId);
        }

        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new UserNotFoundException(authorId));

        // 1. Сохраняем метаданные в БД с временным путем, чтобы получить ID
        Document document = Document.create(title, authorId, spaceId, "pending/" + System.nanoTime());
        Document savedDocument = documentRepository.save(document);

        // 2. Формируем финальный путь и сохраняем контент в Git
        String gitPath = String.format("spaces/%d/%d.md", spaceId, savedDocument.getId());
        savedDocument.updateGitFilePath(gitPath);
        
        contentRepository.saveContent(
                gitPath, 
                content != null ? content : "", 
                "Initial commit for document: " + title,
                author.getLogin(),
                author.getEmail()
        );

        // 3. Обновляем метаданные с корректным путем
        return documentRepository.save(savedDocument);
    }

    /**
     * Возвращает документ по ID.
     * @throws DocumentNotFoundException если не найден
     */
    public Document getDocumentById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
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
    public Document updateDocument(Long id, String title, String content, DocumentStatus status, Long editorId) {
        Document document = getDocumentById(id);
        User editor = userRepository.findById(editorId)
                .orElseThrow(() -> new UserNotFoundException(editorId));

        log.debug("Обновление документа ID {}: title='{}', status={}", id, title, status);

        // Обновляем метаданные в БД
        document.updateMetadata(title, status);
        Document updatedMetadata = documentRepository.save(document);

        // Обновляем контент в Git, если он передан
        if (content != null) {
            contentRepository.saveContent(
                    document.getGitFilePath(),
                    content,
                    "Update document content: " + document.getTitle(),
                    editor.getLogin(),
                    editor.getEmail()
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
     * Возвращает список документов в пространстве.
     */
    public List<Document> getDocumentsInSpace(Long spaceId, boolean includeDeleted) {
        if (!spaceRepository.findById(spaceId).isPresent()) {
            throw new SpaceNotFoundException(spaceId);
        }
        return documentRepository.findBySpaceId(spaceId, includeDeleted);
    }
}
