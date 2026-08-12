package com.knowledgebase.infrastructure.repository.git;

import com.knowledgebase.domain.model.GitCommitResult;
import com.knowledgebase.domain.repository.DocumentContentRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
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
    public synchronized GitCommitResult saveContent(String gitFilePath, String content, String commitMessage,
                                                     String authorName, String authorEmail) {
        return saveSnapshot(null, gitFilePath, content, null, null, commitMessage, authorName, authorEmail);
    }

    @Override
    public synchronized GitCommitResult saveDocumentSnapshot(String oldGitFilePath, String gitFilePath, String content,
                                                              String metadataPath, String metadataContent,
                                                              String commitMessage, String authorName, String authorEmail) {
        if (metadataPath == null || metadataContent == null) {
            throw new IllegalArgumentException("Metadata path and content are required for a document snapshot");
        }
        return saveSnapshot(oldGitFilePath, gitFilePath, content, metadataPath, metadataContent,
                commitMessage, authorName, authorEmail);
    }

    private GitCommitResult saveSnapshot(String oldGitFilePath, String gitFilePath, String content,
                                         String metadataPath, String metadataContent, String commitMessage,
                                         String authorName, String authorEmail) {
        try (Git git = Git.open(new File(gitRepoPath))) {
            writeFile(gitFilePath, content);
            if (metadataPath != null) {
                writeFile(metadataPath, metadataContent);
            }

            if (oldGitFilePath != null && !oldGitFilePath.equals(gitFilePath)) {
                Path oldFile = resolvePath(oldGitFilePath);
                Files.deleteIfExists(oldFile);
                git.rm().addFilepattern(oldGitFilePath).call();
            }
            git.add().addFilepattern(gitFilePath).call();
            if (metadataPath != null) {
                git.add().addFilepattern(metadataPath).call();
            }

            var status = git.status().call();
            Set<String> changedPaths = new HashSet<>(status.getChanged());
            changedPaths.addAll(status.getAdded());
            changedPaths.addAll(status.getModified());
            changedPaths.addAll(status.getRemoved());
            if (changedPaths.stream().noneMatch(path -> path.equals(gitFilePath)
                    || path.equals(metadataPath) || path.equals(oldGitFilePath))) {
                return null;
            }

            RevCommit commit = git.commit()
                    .setMessage(commitMessage)
                    .setAuthor(authorName, authorEmail)
                    .setCommitter(authorName, authorEmail)
                    .call();
            GitCommitResult result = new GitCommitResult(commit.getId().name(),
                    LocalDateTime.ofInstant(commit.getAuthorIdent().getWhenAsInstant(), ZoneOffset.UTC));
            log.debug("Сохранен снимок документа и создан коммит {} для {}", result.hash(), gitFilePath);
            return result;
        } catch (IOException | GitAPIException e) {
            log.error("Ошибка при сохранении контента в Git: {}", gitFilePath, e);
            throw new RuntimeException("Ошибка Git-хранилища", e);
        }
    }

    private void writeFile(String gitFilePath, String content) throws IOException {
        Path filePath = resolvePath(gitFilePath);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content == null ? "" : content);
    }

    private Path resolvePath(String gitFilePath) {
        Path repositoryRoot = Paths.get(gitRepoPath).toAbsolutePath().normalize();
        Path resolved = repositoryRoot.resolve(gitFilePath).normalize();
        if (!resolved.startsWith(repositoryRoot)) {
            throw new IllegalArgumentException("Путь файла выходит за пределы Git-репозитория");
        }
        return resolved;
    }

    @Override
    public Optional<String> findContentByPath(String gitFilePath) {
        Path filePath = resolvePath(gitFilePath);
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
            Path source = resolvePath(oldPath);
            Path target = resolvePath(newPath);
            
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
            Path filePath = resolvePath(gitFilePath);
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
}
