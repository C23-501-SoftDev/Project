package com.knowledgebase.application.service.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebase.domain.model.DocumentVersion;
import com.knowledgebase.domain.repository.DocumentContentRepository;
import com.knowledgebase.domain.repository.DocumentVersionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/** Adds immutable provenance to legacy/first-published documents only when the RAG worker runs. */
@Service
public class RagDocumentBaselineService {
    private static final Logger log = LoggerFactory.getLogger(RagDocumentBaselineService.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final DocumentContentRepository content;
    private final DocumentVersionRepository versions;
    private final ObjectMapper json;
    private long afterDocumentId;

    public RagDocumentBaselineService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                       DocumentContentRepository content, DocumentVersionRepository versions,
                                       ObjectMapper json) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.content = content;
        this.versions = versions;
        this.json = json;
    }

    public synchronized void baselinePublished(int limit) {
        var candidates = jdbc.query("""
                SELECT d.id FROM documents d JOIN spaces s ON s.id = d.space_id
                WHERE d.id > ? AND d.status = 'Published' AND s.is_deleted = false
                    AND NOT EXISTS (SELECT 1 FROM versions v WHERE v.document_id = d.id)
                ORDER BY d.id LIMIT ?
                """, (rs, row) -> rs.getLong(1), afterDocumentId, limit);
        if (candidates.isEmpty()) {
            afterDocumentId = 0;
            return;
        }
        afterDocumentId = candidates.get(candidates.size() - 1);
        for (Long documentId : candidates) {
            try {
                transaction.executeWithoutResult(status -> {
                    var documents = jdbc.query("""
                            SELECT d.id, d.git_file_path, d.author_id, d.updated_at, u.login, u.email
                            FROM documents d JOIN spaces s ON s.id = d.space_id JOIN users u ON u.id = d.author_id
                            WHERE d.id = ? AND d.status = 'Published' AND s.is_deleted = false
                                AND NOT EXISTS (SELECT 1 FROM versions v WHERE v.document_id = d.id)
                            ORDER BY d.id FOR UPDATE OF d SKIP LOCKED LIMIT 1
                            """, (rs, row) -> new BaselineSource(rs.getLong("id"), rs.getString("git_file_path"),
                            rs.getLong("author_id"), rs.getTimestamp("updated_at").toLocalDateTime(),
                            rs.getString("login"), rs.getString("email")), documentId);
                    if (documents.isEmpty()) return;
                    BaselineSource source = documents.get(0);
                    String originalSha = content.latestCommitForPath(source.path())
                            .orElseThrow(() -> new IllegalStateException("Missing baseline Git source"));
                    String markdown = content.readDocumentVersion(source.path(), originalSha)
                            .orElseThrow(() -> new IllegalStateException("Missing baseline content"));
                    String metadataPath = ".metadata/rag-baselines/" + source.id() + ".json";
                    String metadata;
                    try {
                        metadata = json.writeValueAsString(Map.of("documentId", source.id(), "gitFilePath", source.path(),
                                "sourceUpdatedAt", source.updatedAt().toString(), "kind", "rag-baseline"));
                    } catch (Exception ex) {
                        throw new IllegalStateException("Cannot encode baseline metadata", ex);
                    }
                    var commit = content.saveDocumentSnapshot(null, source.path(), markdown, metadataPath, metadata,
                            "Create RAG baseline for published document " + source.id(), source.login(), source.email());
                    // Retrying after a DB failure can find an identical Git snapshot already committed.
                    String sha = commit != null ? commit.hash() : content.latestCommitForPath(metadataPath)
                            .orElseThrow(() -> new IllegalStateException("Missing baseline snapshot"));
                    versions.save(DocumentVersion.create(source.id(), sha, source.path(), source.authorId(),
                            "RAG baseline for published document", commit != null ? commit.committedAt()
                                    : LocalDateTime.now(ZoneOffset.UTC).withNano(0)));
                });
            } catch (RuntimeException ex) {
                // One broken legacy file must not prevent other published articles from being indexed.
                log.warn("RAG baseline failed documentId={} error={}", documentId, ex.getClass().getSimpleName());
            }
        }
    }

    private record BaselineSource(long id, String path, long authorId, LocalDateTime updatedAt,
                                  String login, String email) { }
}
