package com.knowledgebase.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Конфигурация файловых хранилищ.
 * Выполняет требование US4.1.1: Инициализация локальной директории 
 * для Git-репозитория и Blob-хранилища.
 */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Value("${app.storage.git.path:./data/git-repo}")
    private String gitRepoPath;

    @Value("${app.storage.blob.path:./data/blob-storage}")
    private String blobStoragePath;

    @PostConstruct
    public void initStorage() {
        try {
            initGitRepository();
            initBlobStorage();
            log.info("Хранилища успешно инициализированы");
        } catch (Exception e) {
            log.error("Критическая ошибка инициализации файловых хранилищ", e);
            throw new RuntimeException("Не удалось инициализировать хранилища", e);
        }
    }

    private void initGitRepository() throws IOException, GitAPIException {
        Path gitPath = Paths.get(gitRepoPath);
        
        log.info("Инициализация Git-репозитория: {}", gitPath.toAbsolutePath());
        
        // Создаем директорию, если её нет
        if (!Files.exists(gitPath)) {
            Files.createDirectories(gitPath);
            log.info("Создана директория: {}", gitPath);
        }

        // Проверяем, есть ли уже репозиторий
        Path dotGit = gitPath.resolve(".git");
        if (!Files.exists(dotGit)) {
            log.info("Инициализация пустого Git-репозитория...");
            
            // 1. git init
            try (Git git = Git.init().setDirectory(gitPath.toFile()).call()) {
                log.info("Git-репозиторий инициализирован: {}", git.getRepository().getDirectory());
            }
            
            // 2. Создаем первый коммит
            createInitialCommit(gitPath);
            
        } else {
            log.info("Git-репозиторий уже существует: {}", dotGit);
        }
    }

    private void createInitialCommit(Path gitPath) throws IOException, GitAPIException {
        Path readmePath = gitPath.resolve("README.md");
        
        // Создаем README.md
        String content = """
                # Knowledge Base Repository
                
                Этот репозиторий содержит Markdown-файлы документов базы знаний.
                
                ## Структура
                - Каждый документ сохраняется как отдельный `.md` файл
                - Изменения автоматически коммитятся при сохранении
                
                ## Информация
                - **Создан:** %s
                - **Система:** Knowledge Base Backend
                
                ---
                *Этот файл создан автоматически при инициализации системы.*
                """.formatted(java.time.LocalDateTime.now());
        
        Files.writeString(readmePath, content);
        log.info("Создан файл README.md");
        
        // Открываем репозиторий
        try (Git git = Git.open(gitPath.toFile())) {
            
            // git add README.md
            git.add()
               .addFilepattern("README.md")
               .call();
            log.info("Файл добавлен в индекс (git add)");
            
            // git commit
            git.commit()
               .setMessage("Initial commit: system initialization")
               .setAuthor("Knowledge Base System", "system@knowledgebase.local")
               .call();
            log.info("Создан начальный коммит (git commit)");
            
        }
    }

    private void initBlobStorage() throws IOException {
        Path blobPath = Paths.get(blobStoragePath);
        
        log.info("Инициализация Blob-хранилища: {}", blobPath.toAbsolutePath());
        
        if (!Files.exists(blobPath)) {
            Files.createDirectories(blobPath);
            log.info("Создана директория: {}", blobPath);
            
            // Создаем поддиректории
            Files.createDirectories(blobPath.resolve("images"));
            Files.createDirectories(blobPath.resolve("attachments"));
            log.info("Созданы поддиректории: images/, attachments/");
            
            // Создаем .gitkeep для отслеживания пустых папок (опционально)
            Files.createFile(blobPath.resolve("images/.gitkeep"));
            Files.createFile(blobPath.resolve("attachments/.gitkeep"));
            
        } else {
            log.info("Blob-хранилище уже существует: {}", blobPath);
        }
    }
}
