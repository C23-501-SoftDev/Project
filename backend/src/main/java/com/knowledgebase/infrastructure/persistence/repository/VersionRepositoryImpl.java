package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.repository.DocumentContentRepository.CommitLogEntry;
import com.knowledgebase.domain.repository.VersionRepository;
import com.knowledgebase.infrastructure.persistence.entity.VersionJpaEntity;
import com.knowledgebase.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class VersionRepositoryImpl implements VersionRepository {

    private final VersionJpaRepository versionJpaRepository;
    private final UserJpaRepository userJpaRepository;

    public VersionRepositoryImpl(VersionJpaRepository versionJpaRepository, UserJpaRepository userJpaRepository) {
        this.versionJpaRepository = versionJpaRepository;
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public void saveVersion(Long documentId, String gitHash, Long authorId, String comment) {
        VersionJpaEntity entity = new VersionJpaEntity(documentId, gitHash, authorId, comment);
        versionJpaRepository.save(entity);
    }

    @Override
    public List<CommitLogEntry> findVersionsByDocumentId(Long documentId) {
        List<VersionJpaEntity> entities = versionJpaRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
        return entities.stream().map(v -> {
            UserJpaEntity user = userJpaRepository.findById(v.getAuthorId()).orElse(null);
            String authorName = user != null ? (user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getLogin()) : "Unknown";
            String authorEmail = user != null ? user.getEmail() : "";
            return new CommitLogEntry(
                    v.getGitHash(),
                    authorName,
                    authorEmail,
                    v.getComment() != null ? v.getComment() : "",
                    v.getCreatedAt()
            );
        }).collect(Collectors.toList());
    }
}
