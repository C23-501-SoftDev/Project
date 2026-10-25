package com.knowledgebase.infrastructure.persistence.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Durable, rebuildable queue populated only from committed SQL publication/version state. */
@Repository
public class RagIndexJobRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public RagIndexJobRepository(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public void reconcile(String model, int dimensions, int limit) {
        transaction.executeWithoutResult(status -> {
            jdbc.update("""
                    UPDATE rag_index_jobs SET state = 'FAILED', last_error = 'LEASE_EXPIRED',
                        lease_until = NULL, lease_token = NULL, updated_at = CURRENT_TIMESTAMP
                    WHERE state = 'PROCESSING' AND attempts >= 5 AND lease_until < CURRENT_TIMESTAMP
                    """);
            jdbc.update("""
                    DELETE FROM rag_chunks c WHERE NOT EXISTS (
                        SELECT 1 FROM documents d JOIN spaces s ON s.id = d.space_id
                        JOIN LATERAL (SELECT v.git_hash FROM versions v WHERE v.document_id = d.id
                            ORDER BY v.created_at DESC, v.id DESC LIMIT 1) latest ON true
                        WHERE d.id = c.document_id AND d.status = 'Published'
                          AND s.is_deleted = false AND latest.git_hash = c.version_sha
                          AND d.updated_at = c.source_updated_at)
                    """);
            jdbc.update("""
                    DELETE FROM rag_index_jobs j WHERE NOT EXISTS (
                        SELECT 1 FROM documents d JOIN spaces s ON s.id = d.space_id
                        WHERE d.id = j.document_id AND d.status = 'Published' AND s.is_deleted = false)
                    """);
            // Exclude matching jobs before LIMIT so an already-indexed first page cannot starve backfill.
            jdbc.update("""
                    INSERT INTO rag_index_jobs
                        (document_id, space_id, version_sha, git_file_path, source_updated_at, embedding_model,
                         embedding_dimensions, state, attempts, available_at)
                    SELECT d.id, d.space_id, latest.git_hash,
                           COALESCE(latest.git_file_path, d.git_file_path), d.updated_at, ?, ?, 'PENDING', 0, CURRENT_TIMESTAMP
                    FROM documents d JOIN spaces s ON s.id = d.space_id
                    JOIN LATERAL (SELECT v.git_hash, v.git_file_path FROM versions v
                        WHERE v.document_id = d.id ORDER BY v.created_at DESC, v.id DESC LIMIT 1) latest ON true
                    LEFT JOIN rag_index_jobs j ON j.document_id = d.id
                        AND j.embedding_model = ? AND j.embedding_dimensions = ?
                    WHERE d.status = 'Published' AND s.is_deleted = false
                      AND (j.document_id IS NULL OR j.version_sha <> latest.git_hash OR j.source_updated_at <> d.updated_at)
                    ORDER BY d.id LIMIT ?
                    ON CONFLICT (document_id, embedding_model, embedding_dimensions) DO UPDATE SET
                        space_id = EXCLUDED.space_id, version_sha = EXCLUDED.version_sha,
                        git_file_path = EXCLUDED.git_file_path, state = 'PENDING', attempts = 0,
                        source_updated_at = EXCLUDED.source_updated_at,
                        available_at = CURRENT_TIMESTAMP, lease_until = NULL, lease_token = NULL,
                        last_error = NULL, updated_at = CURRENT_TIMESTAMP
                    """, model, dimensions, model, dimensions, limit);
        });
    }

    public Optional<RagIndexJob> claim(String model, int dimensions) {
        String token = UUID.randomUUID().toString();
        List<RagIndexJob> rows = jdbc.query("""
                WITH next_job AS (
                    SELECT document_id, embedding_model, embedding_dimensions FROM rag_index_jobs
                    WHERE embedding_model = ? AND embedding_dimensions = ? AND attempts < 5
                      AND available_at <= CURRENT_TIMESTAMP
                      AND (state = 'PENDING' OR (state = 'PROCESSING' AND lease_until < CURRENT_TIMESTAMP))
                    ORDER BY available_at, document_id FOR UPDATE SKIP LOCKED LIMIT 1
                )
                UPDATE rag_index_jobs j SET state = 'PROCESSING', attempts = attempts + 1,
                    lease_token = ?, lease_until = CURRENT_TIMESTAMP + INTERVAL '5 minutes',
                    updated_at = CURRENT_TIMESTAMP
                FROM next_job n WHERE j.document_id = n.document_id
                    AND j.embedding_model = n.embedding_model
                    AND j.embedding_dimensions = n.embedding_dimensions
                RETURNING j.document_id, j.space_id, j.version_sha, j.git_file_path,
                    j.embedding_model, j.embedding_dimensions, j.lease_token, j.source_updated_at
                """, (rs, row) -> new RagIndexJob(rs.getLong("document_id"), rs.getLong("space_id"),
                rs.getString("version_sha"), rs.getString("git_file_path"), rs.getString("embedding_model"),
                rs.getInt("embedding_dimensions"), rs.getString("lease_token"),
                rs.getTimestamp("source_updated_at").toLocalDateTime()), model, dimensions, token);
        return rows.stream().findFirst();
    }

    public boolean renewLease(RagIndexJob job) {
        return jdbc.update("""
                UPDATE rag_index_jobs SET lease_until = CURRENT_TIMESTAMP + INTERVAL '5 minutes'
                WHERE document_id = ? AND embedding_model = ? AND embedding_dimensions = ?
                    AND lease_token = ? AND state = 'PROCESSING' AND lease_until > CURRENT_TIMESTAMP
                """, job.documentId(), job.embeddingModel(), job.embeddingDimensions(), job.leaseToken()) == 1;
    }

    /** Reservations are charged even on provider failure, conservatively bounding attempted work. */
    public boolean reserveEmbeddingCharacters(long characters, long dailyLimit) {
        if (characters < 1 || characters > dailyLimit) return false;
        List<Long> used = jdbc.query("""
                INSERT INTO rag_embedding_budget (budget_day, used_characters)
                VALUES ((CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date, ?)
                ON CONFLICT (budget_day) DO UPDATE SET
                    used_characters = rag_embedding_budget.used_characters + EXCLUDED.used_characters
                WHERE rag_embedding_budget.used_characters + EXCLUDED.used_characters <= ?
                RETURNING used_characters
                """, (rs, row) -> rs.getLong(1), characters, dailyLimit);
        return !used.isEmpty();
    }

    public void deferForBudget(RagIndexJob job) {
        jdbc.update("""
                UPDATE rag_index_jobs SET state = 'PENDING', attempts = GREATEST(0, attempts - 1),
                    available_at = ((CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date + 1)::timestamp AT TIME ZONE 'UTC',
                    lease_until = NULL, lease_token = NULL, last_error = 'DAILY_BUDGET', updated_at = CURRENT_TIMESTAMP
                WHERE document_id = ? AND embedding_model = ? AND embedding_dimensions = ? AND lease_token = ?
                """, job.documentId(), job.embeddingModel(), job.embeddingDimensions(), job.leaseToken());
    }

    public void fail(RagIndexJob job, String errorCode) {
        jdbc.update("""
                UPDATE rag_index_jobs SET state = CASE WHEN attempts >= 5 THEN 'FAILED' ELSE 'PENDING' END,
                    available_at = CURRENT_TIMESTAMP + INTERVAL '1 minute' * LEAST(60, power(2, attempts)),
                    lease_until = NULL, lease_token = NULL, last_error = ?, updated_at = CURRENT_TIMESTAMP
                WHERE document_id = ? AND embedding_model = ? AND embedding_dimensions = ? AND lease_token = ?
                """, errorCode, job.documentId(), job.embeddingModel(), job.embeddingDimensions(), job.leaseToken());
    }

    /** No partial chunks are exposed; expired workers cannot overwrite a newer lease or source version. */
    public boolean complete(RagIndexJob job, List<String> chunks, List<float[]> embeddings) {
        if (chunks.size() != embeddings.size()) throw new IllegalArgumentException("Embedding count differs");
        for (float[] embedding : embeddings) {
            if (embedding.length != job.embeddingDimensions()) throw new IllegalArgumentException("Embedding dimensions differ");
            PgVectorRagIndexRepository.vectorLiteral(embedding);
        }
        return Boolean.TRUE.equals(transaction.execute(status -> {
            List<Long> locked = jdbc.query("""
                    SELECT document_id FROM rag_index_jobs WHERE document_id = ? AND embedding_model = ?
                        AND embedding_dimensions = ? AND lease_token = ? AND state = 'PROCESSING'
                        AND lease_until > CURRENT_TIMESTAMP FOR UPDATE
                    """, (rs, row) -> rs.getLong(1), job.documentId(), job.embeddingModel(),
                    job.embeddingDimensions(), job.leaseToken());
            if (locked.isEmpty()) return false;
            Boolean current = jdbc.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM documents d JOIN spaces s ON s.id = d.space_id
                        JOIN LATERAL (SELECT git_hash FROM versions v WHERE v.document_id = d.id
                            ORDER BY created_at DESC, id DESC LIMIT 1) latest ON true
                        WHERE d.id = ? AND d.status = 'Published' AND s.is_deleted = false
                            AND latest.git_hash = ? AND d.updated_at = ?)
                    """, Boolean.class, job.documentId(), job.versionSha(), job.sourceUpdatedAt());
            if (!Boolean.TRUE.equals(current)) {
                jdbc.update("""
                        DELETE FROM rag_index_jobs WHERE document_id = ? AND embedding_model = ?
                            AND embedding_dimensions = ? AND lease_token = ?
                        """, job.documentId(), job.embeddingModel(), job.embeddingDimensions(), job.leaseToken());
                return false;
            }
            jdbc.update("DELETE FROM rag_chunks WHERE document_id = ? AND embedding_model = ? AND embedding_dimensions = ?",
                    job.documentId(), job.embeddingModel(), job.embeddingDimensions());
            for (int index = 0; index < chunks.size(); index++) {
                jdbc.update("""
                        INSERT INTO rag_chunks (document_id, space_id, version_sha, source_updated_at, chunk_index, chunk_text,
                            embedding_model, embedding_dimensions, embedding)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector))
                        """, job.documentId(), job.spaceId(), job.versionSha(), job.sourceUpdatedAt(), index, chunks.get(index),
                        job.embeddingModel(), job.embeddingDimensions(), PgVectorRagIndexRepository.vectorLiteral(embeddings.get(index)));
            }
            jdbc.update("""
                    UPDATE rag_index_jobs SET state = 'DONE', lease_until = NULL, lease_token = NULL,
                        last_error = NULL, updated_at = CURRENT_TIMESTAMP
                    WHERE document_id = ? AND embedding_model = ? AND embedding_dimensions = ? AND lease_token = ?
                    """, job.documentId(), job.embeddingModel(), job.embeddingDimensions(), job.leaseToken());
            return true;
        }));
    }
}
