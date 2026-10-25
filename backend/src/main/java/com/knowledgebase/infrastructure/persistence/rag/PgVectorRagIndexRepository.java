package com.knowledgebase.infrastructure.persistence.rag;

import com.knowledgebase.domain.rag.RagChunk;
import com.knowledgebase.domain.repository.RagIndexRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class PgVectorRagIndexRepository implements RagIndexRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public PgVectorRagIndexRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public boolean isAvailable() {
        try {
            return Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT to_regclass('rag_chunks') IS NOT NULL
                        AND to_regclass('rag_index_jobs') IS NOT NULL
                        AND EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')
                    """, Boolean.class));
        } catch (DataAccessException ex) {
            return false;
        }
    }

    @Override
    public List<RagChunk> search(float[] query, String model, List<Long> authorizedDocumentIds,
                                 int limit, double minimumScore, int maxChunksPerDocument) {
        if (authorizedDocumentIds == null || authorizedDocumentIds.isEmpty()) return List.of();
        if (limit < 1 || limit > 100 || maxChunksPerDocument < 1 || maxChunksPerDocument > 20 || !Double.isFinite(minimumScore)
                || minimumScore < -1 || minimumScore > 1) {
            throw new IllegalArgumentException("Invalid RAG retrieval bounds");
        }
        var parameters = new MapSqlParameterSource()
                .addValue("vector", vectorLiteral(query))
                .addValue("dimensions", query.length)
                .addValue("model", Objects.requireNonNull(model))
                .addValue("documentIds", authorizedDocumentIds)
                .addValue("limit", limit).addValue("minimumScore", minimumScore)
                .addValue("maxChunksPerDocument", maxChunksPerDocument);
        // MATERIALIZED keeps dimensionality filtering before the vector operator.
        // SQL metadata and latest version are authoritative even before async cleanup runs.
        return named.query("""
                WITH eligible AS MATERIALIZED (
                    SELECT c.document_id, d.space_id, c.version_sha, c.chunk_index,
                           c.chunk_text, c.embedding, c.source_updated_at
                    FROM rag_chunks c
                    JOIN documents d ON d.id = c.document_id
                    JOIN spaces s ON s.id = d.space_id
                    JOIN LATERAL (
                        SELECT v.git_hash FROM versions v WHERE v.document_id = d.id
                        ORDER BY v.created_at DESC, v.id DESC LIMIT 1
                    ) latest ON latest.git_hash = c.version_sha
                    WHERE d.status = 'Published' AND s.is_deleted = false
                      AND d.updated_at = c.source_updated_at
                      AND c.document_id IN (:documentIds)
                      AND c.embedding_model = :model
                      AND c.embedding_dimensions = :dimensions
                ), scored AS (
                    SELECT *, 1 - (embedding <=> CAST(:vector AS vector)) AS score
                    FROM eligible
                ), ranked AS (
                    SELECT *, row_number() OVER (
                        PARTITION BY document_id ORDER BY score DESC, chunk_index
                    ) AS document_rank
                    FROM scored WHERE score >= :minimumScore
                )
                SELECT document_id, space_id, version_sha, chunk_index, chunk_text, score, source_updated_at
                FROM ranked WHERE document_rank <= :maxChunksPerDocument
                ORDER BY score DESC, document_id, chunk_index LIMIT :limit
                """, parameters, (rs, row) -> new RagChunk(rs.getLong("document_id"),
                rs.getLong("space_id"), rs.getString("version_sha"), rs.getInt("chunk_index"),
                rs.getString("chunk_text"), rs.getDouble("score"), rs.getTimestamp("source_updated_at").toLocalDateTime()));
    }

    static String vectorLiteral(float[] vector) {
        if (vector == null || vector.length == 0 || vector.length > 16000) {
            throw new IllegalArgumentException("Invalid embedding dimensions");
        }
        StringBuilder value = new StringBuilder("[");
        double norm = 0;
        for (int i = 0; i < vector.length; i++) {
            if (!Float.isFinite(vector[i])) throw new IllegalArgumentException("Non-finite embedding");
            if (i > 0) value.append(',');
            value.append(vector[i]);
            norm += (double) vector[i] * vector[i];
        }
        if (norm == 0) throw new IllegalArgumentException("Zero embedding");
        return value.append(']').toString();
    }
}
