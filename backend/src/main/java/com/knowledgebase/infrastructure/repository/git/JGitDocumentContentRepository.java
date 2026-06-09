package com.knowledgebase.infrastructure.repository.git;

import com.knowledgebase.domain.repository.DocumentContentRepository;
import com.knowledgebase.infrastructure.logging.SystemLogger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Repository
public class JGitDocumentContentRepository implements DocumentContentRepository {

    private static final SystemLogger log = SystemLogger.getLogger(JGitDocumentContentRepository.class, "repository.git_content");

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

                log.debug("Git content operation completed", "save_content", "success");
            }
        } catch (IOException | GitAPIException ex) {
            log.error("Git content operation failed", "save_content", ex);
            throw new RuntimeException("Git storage error", ex);
        }
    }

    @Override
    public Optional<String> findContentByPath(String gitFilePath) {
        Path filePath = Paths.get(gitRepoPath, gitFilePath);
        if (!Files.exists(filePath)) {
            log.warn("Git content not found", "find_content", "not_found");
            return Optional.empty();
        }

        try {
            return Optional.of(Files.readString(filePath));
        } catch (IOException ex) {
            log.error("Git content operation failed", "find_content", ex);
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

                git.rm().addFilepattern(oldPath).call();
                git.add().addFilepattern(newPath).call();

                git.commit()
                        .setMessage(commitMessage)
                        .call();
                log.debug("Git content operation completed", "move_content", "success");
            } else {
                log.warn("Git content not found", "move_content", "not_found");
            }
        } catch (IOException | GitAPIException ex) {
            log.error("Git content operation failed", "move_content", ex);
            throw new RuntimeException("Git storage error", ex);
        }
    }

    @Override
    public void deleteContent(String gitFilePath, String commitMessage) {
        try (Git git = Git.open(new File(gitRepoPath))) {
            Path filePath = Paths.get(gitRepoPath, gitFilePath);
            if (Files.exists(filePath)) {
                git.rm().addFilepattern(gitFilePath).call();
                git.commit().setMessage(commitMessage).call();
                log.debug("Git content operation completed", "delete_content", "success");
            } else {
                log.warn("Git content not found", "delete_content", "not_found");
            }
        } catch (IOException | GitAPIException ex) {
            log.error("Git content operation failed", "delete_content", ex);
            throw new RuntimeException("Git storage error", ex);
        }
    }
}
