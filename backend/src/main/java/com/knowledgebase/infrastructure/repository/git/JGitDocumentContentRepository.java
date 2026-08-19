package com.knowledgebase.infrastructure.repository.git;

import com.knowledgebase.domain.model.GitCommitResult;
import com.knowledgebase.domain.model.DiffLine;
import com.knowledgebase.domain.model.DiffLineType;
import com.knowledgebase.domain.exception.DocumentDiffTooLargeException;
import com.knowledgebase.domain.repository.DocumentContentRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.treewalk.TreeWalk;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Реализация хранилища контента на основе JGit.
 */
@Repository
public class JGitDocumentContentRepository implements DocumentContentRepository {

    private static final Logger log = LoggerFactory.getLogger(JGitDocumentContentRepository.class);
    private static final int DIFF_CONTEXT_LINES = 3;

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
    public synchronized List<DiffLine> diffDocumentVersions(String fromPath, String toPath, String fromHash,
                                                             String toHash, int maxLines, int maxBytes) {
        return diffDocumentVersions(fromPath, toPath, fromHash, toHash, maxLines, maxBytes, false);
    }

    @Override
    public synchronized List<DiffLine> diffDocumentVersions(String fromPath, String toPath, String fromHash,
                                                             String toHash, int maxLines, int maxBytes,
                                                             boolean includeAllContext) {
        try (Git git = Git.open(new File(gitRepoPath))) {
            org.eclipse.jgit.lib.Repository repository = git.getRepository();
            RawText before = new RawText(readVersionFile(repository, fromHash, fromPath, maxBytes));
            RawText after = new RawText(readVersionFile(repository, toHash, toPath, maxBytes));
            EditList edits = DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM)
                    .diff(RawTextComparator.DEFAULT, before, after);
            return toDiffLines(before, after, edits, maxLines, includeAllContext);
        } catch (IOException e) {
            log.error("Ошибка при сравнении версий документа from={} to={}", fromHash, toHash, e);
            throw new RuntimeException("Ошибка Git-хранилища", e);
        }
    }

    private byte[] readVersionFile(org.eclipse.jgit.lib.Repository repository, String hash, String path,
                                   int maxBytes) throws IOException {
        ObjectId commitId = repository.resolve(hash);
        if (commitId == null) {
            throw new IllegalArgumentException("Git-коммит не найден");
        }
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(commitId);
            try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, commit.getTree())) {
                if (treeWalk == null) {
                    return new byte[0];
                }
                ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                if (loader.getSize() > maxBytes) {
                    throw new DocumentDiffTooLargeException(maxBytes);
                }
                return loader.getCachedBytes(maxBytes);
            }
        }
    }

    private List<DiffLine> toDiffLines(RawText before, RawText after, EditList edits, int maxLines,
                                       boolean includeAllContext) {
        if (edits.isEmpty()) {
            if (!includeAllContext) {
                return List.of();
            }
            List<DiffLine> unchanged = new ArrayList<>();
            for (int index = 0; index < before.size(); index++) {
                addLine(unchanged, new DiffLine(DiffLineType.CONTEXT, index + 1, index + 1,
                        before.getString(index)), maxLines);
            }
            return List.copyOf(unchanged);
        }
        List<DiffLine> lines = new ArrayList<>();
        int beforeIndex = 0;
        int afterIndex = 0;
        for (Edit edit : edits) {
            int contextStartBefore = includeAllContext
                    ? beforeIndex : Math.max(beforeIndex, edit.getBeginA() - DIFF_CONTEXT_LINES);
            int contextStartAfter = includeAllContext
                    ? afterIndex : Math.max(afterIndex, edit.getBeginB() - DIFF_CONTEXT_LINES);
            beforeIndex = contextStartBefore;
            afterIndex = contextStartAfter;
            while (beforeIndex < edit.getBeginA() && afterIndex < edit.getBeginB()) {
                addLine(lines, new DiffLine(DiffLineType.CONTEXT, beforeIndex + 1, afterIndex + 1,
                        before.getString(beforeIndex)), maxLines);
                beforeIndex++;
                afterIndex++;
            }
            appendEdit(lines, before, after, edit, maxLines);
            beforeIndex = edit.getEndA();
            afterIndex = edit.getEndB();
        }
        int contextEndBefore = includeAllContext
                ? before.size() : Math.min(before.size(), beforeIndex + DIFF_CONTEXT_LINES);
        int contextEndAfter = includeAllContext
                ? after.size() : Math.min(after.size(), afterIndex + DIFF_CONTEXT_LINES);
        while (beforeIndex < contextEndBefore && afterIndex < contextEndAfter) {
            addLine(lines, new DiffLine(DiffLineType.CONTEXT, beforeIndex + 1, afterIndex + 1,
                    before.getString(beforeIndex)), maxLines);
            beforeIndex++;
            afterIndex++;
        }
        while (beforeIndex < contextEndBefore) {
            addLine(lines, new DiffLine(DiffLineType.REMOVED, beforeIndex + 1, null,
                    before.getString(beforeIndex++)), maxLines);
        }
        while (afterIndex < contextEndAfter) {
            addLine(lines, new DiffLine(DiffLineType.ADDED, null, afterIndex + 1,
                    after.getString(afterIndex++)), maxLines);
        }
        return List.copyOf(lines);
    }

    private void appendEdit(List<DiffLine> lines, RawText before, RawText after, Edit edit, int maxLines) {
        int beforeIndex = edit.getBeginA();
        int afterIndex = edit.getBeginB();
        while (beforeIndex < edit.getEndA() && afterIndex < edit.getEndB()) {
            String beforeLine = before.getString(beforeIndex);
            String afterLine = after.getString(afterIndex);
            if (beforeLine.equals(afterLine)) {
                addLine(lines, new DiffLine(DiffLineType.CONTEXT, beforeIndex + 1, afterIndex + 1,
                        beforeLine), maxLines);
            } else {
                addLine(lines, new DiffLine(DiffLineType.REMOVED, beforeIndex + 1, null,
                        beforeLine), maxLines);
                addLine(lines, new DiffLine(DiffLineType.ADDED, null, afterIndex + 1,
                        afterLine), maxLines);
            }
            beforeIndex++;
            afterIndex++;
        }
        while (beforeIndex < edit.getEndA()) {
            addLine(lines, new DiffLine(DiffLineType.REMOVED, beforeIndex + 1, null,
                    before.getString(beforeIndex++)), maxLines);
        }
        while (afterIndex < edit.getEndB()) {
            addLine(lines, new DiffLine(DiffLineType.ADDED, null, afterIndex + 1,
                    after.getString(afterIndex++)), maxLines);
        }
    }

    private void addLine(List<DiffLine> lines, DiffLine line, int maxLines) {
        if (lines.size() >= maxLines) {
            throw new DocumentDiffTooLargeException(maxLines);
        }
        lines.add(line);
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
