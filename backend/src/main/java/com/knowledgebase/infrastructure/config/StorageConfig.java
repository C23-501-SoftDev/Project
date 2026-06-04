package com.knowledgebase.infrastructure.config;

import com.knowledgebase.infrastructure.logging.SystemLogger;
import jakarta.annotation.PostConstruct;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StorageConfig {

    private static final SystemLogger log = SystemLogger.getLogger(StorageConfig.class, "storage.config");

    @Value("${app.storage.git.path:./data/git-repo}")
    private String gitRepoPath;

    @Value("${app.storage.blob.path:./data/blob-storage}")
    private String blobStoragePath;

    @PostConstruct
    public void initStorage() {
        try {
            initGitRepository();
            initBlobStorage();
            log.info("Storage initialized successfully", "storage_init", "success");
        } catch (Exception ex) {
            log.error("Storage initialization failed", "storage_init", ex);
            throw new RuntimeException("Failed to initialize storage", ex);
        }
    }

    private void initGitRepository() throws IOException, GitAPIException {
        Path gitPath = Paths.get(gitRepoPath);

        log.info("Storage initialization started", "init_git_storage", "started");

        if (!Files.exists(gitPath)) {
            Files.createDirectories(gitPath);
            log.info("Storage directory created", "init_git_storage", "created");
        }

        Path dotGit = gitPath.resolve(".git");
        if (!Files.exists(dotGit)) {
            log.info("Git repository initialization started", "init_git_repository", "started");

            try (Git git = Git.init().setDirectory(gitPath.toFile()).call()) {
                log.info("Git repository initialized", "init_git_repository", "success");
            }

            createInitialCommit(gitPath);
        } else {
            log.info("Git repository already exists", "init_git_repository", "already_exists");
        }
    }

    private void createInitialCommit(Path gitPath) throws IOException, GitAPIException {
        Path readmePath = gitPath.resolve("README.md");

        String content = """
                # Knowledge Base Repository

                This repository contains Knowledge Base Markdown documents.

                ## Structure
                - Each document is stored as a separate `.md` file.
                - Changes are committed automatically when content is saved.

                ## Info
                - **Created:** %s
                - **System:** Knowledge Base Backend

                ---
                *This file was generated automatically during system initialization.*
                """.formatted(java.time.LocalDateTime.now());

        Files.writeString(readmePath, content);
        log.info("Initial repository README created", "init_git_repository", "readme_created");

        try (Git git = Git.open(gitPath.toFile())) {
            git.add()
                    .addFilepattern("README.md")
                    .call();
            log.info("Initial repository README staged", "init_git_repository", "readme_staged");

            git.commit()
                    .setMessage("Initial commit: system initialization")
                    .setAuthor("Knowledge Base System", "system@knowledgebase.local")
                    .call();
            log.info("Initial repository commit created", "init_git_repository", "success");
        }
    }

    private void initBlobStorage() throws IOException {
        Path blobPath = Paths.get(blobStoragePath);

        log.info("Storage initialization started", "init_blob_storage", "started");

        if (!Files.exists(blobPath)) {
            Files.createDirectories(blobPath);
            log.info("Storage directory created", "init_blob_storage", "created");

            Files.createDirectories(blobPath.resolve("images"));
            Files.createDirectories(blobPath.resolve("attachments"));
            log.info("Blob storage subdirectories created", "init_blob_storage", "subdirectories_created");

            Files.createFile(blobPath.resolve("images/.gitkeep"));
            Files.createFile(blobPath.resolve("attachments/.gitkeep"));
        } else {
            log.info("Blob storage already exists", "init_blob_storage", "already_exists");
        }
    }
}
