package com.knowledgebase.infrastructure.repository.git;

import com.knowledgebase.domain.repository.DocumentContentRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Реализация хранилища контента на основе JGit.
 */
@Repository
public class JGitDocumentContentRepository implements DocumentContentRepository {

    private static final Logger log = LoggerFactory.getLogger(JGitDocumentContentRepository.class);

    private final String gitRepoPath;

    public JGitDocumentContentRepository(@Value("${app.storage.git.path}") String gitRepoPath) {
        this.gitRepoPath = gitRepoPath;
    }

    @Override
    public void saveContent(String gitFilePath, String content, String commitMessage, String authorName, String authorEmail) {
        try {
            Path filePath = Paths.get(gitRepoPath, gitFilePath);
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content);

            try (Git git = Git.open(new File(gitRepoPath))) {
                git.add()
                        .addFilepattern(gitFilePath)
                        .call();

                git.commit()
                        .setMessage(commitMessage)
                        .setAuthor(authorName, authorEmail)
                        .call();
                
                log.debug("Сохранен контент и создан коммит для файла: {}", gitFilePath);
            }
        } catch (IOException | GitAPIException e) {
            log.error("Ошибка при сохранении контента в Git: {}", gitFilePath, e);
            throw new RuntimeException("Ошибка Git-хранилища", e);
        }
    }

    @Override
    public Optional<String> findContentByPath(String gitFilePath) {
        Path filePath = Paths.get(gitRepoPath, gitFilePath);
        if (!Files.exists(filePath)) {
            log.warn("Файл не найден в Git-репозитории: {}", filePath);
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(filePath));
        } catch (IOException e) {
            log.error("Ошибка при чтении контента из Git: {}", gitFilePath, e);
            return Optional.empty();
        }
    }

    @Override
    public void moveContent(String oldPath, String newPath, String commitMessage) {
        try (Git git = Git.open(new File(gitRepoPath))) {
            Path source = Paths.get(gitRepoPath, oldPath);
            Path target = Paths.get(gitRepoPath, newPath);
            
            if (Files.exists(source)) {
                Files.createDirectories(target.getParent());
                Files.move(source, target);
                
                // В JGit удаление старого пути и добавление нового
                git.rm().addFilepattern(oldPath).call();
                git.add().addFilepattern(newPath).call();
                
                git.commit()
                        .setMessage(commitMessage)
                        .call();
                log.debug("Файл перемещен в Git: {} -> {}", oldPath, newPath);
            } else {
                log.warn("Попытка переместить несуществующий файл в Git: {}", oldPath);
            }
        } catch (IOException | GitAPIException e) {
            log.error("Ошибка при перемещении файла в Git: {} -> {}", oldPath, newPath, e);
            throw new RuntimeException("Ошибка Git-хранилища", e);
        }
    }

    @Override
    public void deleteContent(String gitFilePath, String commitMessage) {
        try (Git git = Git.open(new File(gitRepoPath))) {
            Path filePath = Paths.get(gitRepoPath, gitFilePath);
            if (Files.exists(filePath)) {
                git.rm().addFilepattern(gitFilePath).call();
                git.commit().setMessage(commitMessage).call();
                log.debug("Файл удален из Git: {}", gitFilePath);
            } else {
                log.warn("Попытка удалить несуществующий файл из Git: {}", gitFilePath);
            }
        } catch (IOException | GitAPIException e) {
            log.error("Ошибка при удалении файла из Git: {}", gitFilePath, e);
            throw new RuntimeException("Ошибка Git-хранилища", e);
        }
    }

    @Override
    public List<CommitLogEntry> getHistory(String gitFilePath) {
        List<CommitLogEntry> history = new ArrayList<>();
        File repoDir = new File(gitRepoPath);
        if (!repoDir.exists()) {
            log.warn("Git репозиторий не найден по пути: {}", repoDir.getAbsolutePath());
            return history;
        }
        try (Git git = Git.open(repoDir)) {
            // Проверяем несколько вариантов путей для надежности (текущий путь, .archive/путь и обратные варианты)
            List<String> pathsToCheck = new ArrayList<>();
            if (gitFilePath != null) {
                pathsToCheck.add(gitFilePath);
                if (gitFilePath.startsWith(".archive/")) {
                    pathsToCheck.add(gitFilePath.substring(".archive/".length()));
                } else {
                    pathsToCheck.add(".archive/" + gitFilePath);
                }
            }

            for (String path : pathsToCheck) {
                try {
                    Iterable<RevCommit> commits = git.log().addPath(path).call();
            for (RevCommit commit : commits) {
                String commitId = commit.getName();
                        String authorName = commit.getAuthorIdent() != null ? commit.getAuthorIdent().getName() : "Unknown";
                        String authorEmail = commit.getAuthorIdent() != null ? commit.getAuthorIdent().getEmailAddress() : "";
                        String commitMessage = commit.getFullMessage() != null ? commit.getFullMessage().trim() : "";
                java.time.LocalDateTime timestamp = java.time.LocalDateTime.ofInstant(
                        commit.getAuthorIdent().getWhen().toInstant(),
                        java.time.ZoneId.systemDefault()
                );
                        // Избегаем дубликатов, если коммит уже добавлен
                        if (history.stream().noneMatch(e -> e.commitId().equals(commitId))) {
                history.add(new CommitLogEntry(commitId, authorName, authorEmail, commitMessage, timestamp));
            }
        }
                } catch (Exception ex) {
                    log.debug("Не удалось получить историю для пути {}: {}", path, ex.getMessage());
    }
}

            // Если по конкретным путям ничего не нашлось, пробуем получить всю историю репозитория,
            // чтобы пользователь видел хотя бы общие коммиты, или ищем по части названия файла
            if (history.isEmpty()) {
                try {
                    Iterable<RevCommit> allCommits = git.log().all().call();
                    for (RevCommit commit : allCommits) {
                        String commitId = commit.getName();
                        String authorName = commit.getAuthorIdent() != null ? commit.getAuthorIdent().getName() : "Unknown";
                        String authorEmail = commit.getAuthorIdent() != null ? commit.getAuthorIdent().getEmailAddress() : "";
                        String commitMessage = commit.getFullMessage() != null ? commit.getFullMessage().trim() : "";

                        java.time.LocalDateTime timestamp = java.time.LocalDateTime.ofInstant(
                                commit.getAuthorIdent().getWhen().toInstant(),
                                java.time.ZoneId.systemDefault()
                        );
                        if (history.stream().noneMatch(e -> e.commitId().equals(commitId))) {
                            history.add(new CommitLogEntry(commitId, authorName, authorEmail, commitMessage, timestamp));
                        }
                    }
                } catch (Exception ex) {
                    log.debug("Не удалось получить общую историю репозитория: {}", ex.getMessage());
                }
            }

            // Сортируем от новых к старым
            history.sort((a, b) -> b.timestamp().compareTo(a.timestamp()));

        } catch (IOException e) {
            log.error("Ошибка при получении истории коммитов из Git для файла: {}", gitFilePath, e);
        }
        return history;
    }
}

