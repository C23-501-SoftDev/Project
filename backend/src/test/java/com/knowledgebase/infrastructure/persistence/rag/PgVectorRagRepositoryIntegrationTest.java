package com.knowledgebase.infrastructure.persistence.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebase.application.service.rag.RagDocumentBaselineService;
import com.knowledgebase.domain.model.DocumentVersion;
import com.knowledgebase.domain.repository.DocumentVersionRepository;
import com.knowledgebase.infrastructure.repository.git.JGitDocumentContentRepository;
import org.eclipse.jgit.api.Git;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real pgvector test in a temporary schema, never resets the configured database. */
@EnabledIfEnvironmentVariable(named = "RAG_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PgVectorRagRepositoryIntegrationTest {
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private PgVectorRagIndexRepository index;
    private RagIndexJobRepository jobs;
    private String schema;
    private DriverManagerDataSource dataSource;
    private static final String MODEL = "fixture-model-v1";
    private static final String SHA = "a".repeat(40);
    private static final LocalDateTime UPDATED = LocalDateTime.of(2026, 10, 26, 12, 0);

    @BeforeAll
    void createSchemaAndApplyActualMigration() throws Exception {
        String url = System.getenv("RAG_TEST_JDBC_URL");
        String user = System.getenv().getOrDefault("RAG_TEST_DB_USER", "kb_user");
        String password = System.getenv().getOrDefault("RAG_TEST_DB_PASSWORD", "local-test");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "rag_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE EXTENSION IF NOT EXISTS vector");
        admin.execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?")
                + "currentSchema=" + schema + ",public", user, password);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE spaces (id BIGINT PRIMARY KEY, is_deleted BOOLEAN NOT NULL DEFAULT false)");
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, login TEXT NOT NULL, email TEXT NOT NULL)");
        jdbc.execute("INSERT INTO users VALUES (1, 'author', 'author@example.test')");
        jdbc.execute("CREATE TABLE documents (id BIGINT PRIMARY KEY, space_id BIGINT NOT NULL, status TEXT NOT NULL, git_file_path TEXT, updated_at TIMESTAMP NOT NULL, author_id BIGINT DEFAULT 1)");
        jdbc.execute("CREATE TABLE versions (id BIGSERIAL PRIMARY KEY, document_id BIGINT NOT NULL, git_hash VARCHAR(40) NOT NULL, git_file_path TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, author_id BIGINT DEFAULT 1, comment TEXT)");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var stream = getClass().getResourceAsStream("/db/changelog/changes/037-add-opt-in-rag-vector-index.xml")) {
            var document = factory.newDocumentBuilder().parse(stream);
            String migration = document.getElementsByTagName("sql").item(0).getTextContent();
            for (String statement : migration.split(";")) if (!statement.isBlank()) jdbc.execute(statement.trim());
        }
        index = new PgVectorRagIndexRepository(jdbc);
        jobs = new RagIndexJobRepository(jdbc, new DataSourceTransactionManager(dataSource));
    }

    @AfterAll
    void dropSchema() {
        if (admin != null && schema != null) admin.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    @BeforeEach
    void sourceFixtures() {
        jdbc.execute("TRUNCATE rag_chunks, rag_index_jobs, rag_embedding_budget, versions, documents, spaces CASCADE");
        jdbc.update("INSERT INTO spaces (id) VALUES (10), (20)");
        for (long document : List.of(1L, 2L)) {
            jdbc.update("INSERT INTO documents (id, space_id, status, git_file_path, updated_at) VALUES (?, ?, 'Published', ?, ?)", document, document * 10,
                    "spaces/" + document + ".md", UPDATED);
            jdbc.update("INSERT INTO versions (document_id, git_hash, git_file_path) VALUES (?, ?, ?)", document, SHA, "spaces/" + document + ".md");
        }
    }

    private RagIndexJob completeFirstJob() {
        jobs.reconcile(MODEL, 2, 100);
        RagIndexJob job = jobs.claim(MODEL, 2).orElseThrow();
        assertTrue(jobs.complete(job, List.of("Опубликованный факт"), List.of(new float[]{1, 0})));
        return job;
    }

    @Test
    void exactVectorSearchFiltersAuthorizedIdsBeforeScoring() {
        completeFirstJob();
        var second = jobs.claim(MODEL, 2).orElseThrow();
        jobs.complete(second, List.of("Закрытый факт"), List.of(new float[]{1, 0}));
        assertTrue(index.isAvailable());
        var found = index.search(new float[]{1, 0}, MODEL, List.of(1L), 5, .3);
        assertEquals(1, found.size());
        assertEquals(1, found.get(0).documentId());
        assertEquals(UPDATED, found.get(0).sourceUpdatedAt());
        assertEquals(1, found.get(0).score(), .000001);
        assertTrue(index.search(new float[]{1, 0}, MODEL, List.of(), 5, .3).isEmpty());
    }

    @Test
    void unicodeCorpusRetrievalReturnsOnlyFiveHighestScoringSources() {
        for (long document = 3; document <= 7; document++) {
            jdbc.update("INSERT INTO documents (id, space_id, status, git_file_path, updated_at) VALUES (?, 10, 'Published', ?, ?)",
                    document, "spaces/" + document + ".md", UPDATED);
            jdbc.update("INSERT INTO versions (document_id, git_hash, git_file_path) VALUES (?, ?, ?)",
                    document, SHA, "spaces/" + document + ".md");
        }
        jobs.reconcile(MODEL, 2, 100);
        for (int i = 0; i < 7; i++) {
            var job = jobs.claim(MODEL, 2).orElseThrow();
            assertTrue(jobs.complete(job, List.of("Статья " + job.documentId() + " 😀"),
                    List.of(new float[]{1, job.documentId() / 10.0f})));
        }
        var found = index.search(new float[]{1, 0}, MODEL, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L), 5, 0);
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), found.stream().map(value -> value.documentId()).toList());
        assertEquals("Статья 1 😀", found.get(0).text());
        assertEquals("Статья 5 😀", found.get(4).text());
        assertTrue(found.stream().allMatch(value -> Double.isFinite(value.score())));
    }

    @Test
    void currentVersionAndTimestampExcludeStaleChunksBeforeCleanup() {
        completeFirstJob();
        jdbc.update("INSERT INTO versions (document_id, git_hash, git_file_path, created_at) VALUES (1, ?, 'spaces/1.md', CURRENT_TIMESTAMP + INTERVAL '1 second')", "b".repeat(40));
        assertTrue(index.search(new float[]{1, 0}, MODEL, List.of(1L), 5, 0).isEmpty());
        jdbc.update("DELETE FROM versions WHERE git_hash = ?", "b".repeat(40));
        jdbc.update("UPDATE documents SET updated_at = updated_at + INTERVAL '1 second' WHERE id = 1");
        assertTrue(index.search(new float[]{1, 0}, MODEL, List.of(1L), 5, 0).isEmpty());
    }

    @Test
    void deletedDocumentsAndSpacesAreImmediatelyExcludedAndPurged() {
        completeFirstJob();
        jdbc.update("UPDATE spaces SET is_deleted = true WHERE id = 10");
        assertTrue(index.search(new float[]{1, 0}, MODEL, List.of(1L), 5, 0).isEmpty());
        jdbc.update("UPDATE spaces SET is_deleted = false WHERE id = 10");
        jdbc.update("UPDATE documents SET status = 'Deleted' WHERE id = 1");
        assertTrue(index.search(new float[]{1, 0}, MODEL, List.of(1L), 5, 0).isEmpty());
        jobs.reconcile(MODEL, 2, 100);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM rag_chunks", Integer.class));
    }

    @Test
    void differentDimensionsAndModelsCannotReachCosineOperator() {
        completeFirstJob();
        assertTrue(index.search(new float[]{1, 0, 0}, MODEL, List.of(1L), 5, 0).isEmpty());
        assertTrue(index.search(new float[]{1, 0}, "another-model", List.of(1L), 5, 0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> index.search(new float[]{0, 0}, MODEL, List.of(1L), 5, 0));
        assertThrows(IllegalArgumentException.class, () -> index.search(new float[]{Float.NaN, 0}, MODEL, List.of(1L), 5, 0));
    }

    @Test
    void reconciliationIsIdempotentAndDoesNotStarveBackfill() {
        jobs.reconcile(MODEL, 2, 1);
        var first = jobs.claim(MODEL, 2).orElseThrow();
        assertTrue(jobs.complete(first, List.of("a"), List.of(new float[]{1, 0})));
        jobs.reconcile(MODEL, 2, 1);
        var second = jobs.claim(MODEL, 2).orElseThrow();
        assertEquals(2, second.documentId());
        assertTrue(jobs.complete(second, List.of("b"), List.of(new float[]{1, 0})));
        jobs.reconcile(MODEL, 2, 100);
        assertTrue(jobs.claim(MODEL, 2).isEmpty());
        assertFalse(jobs.complete(first, List.of("duplicate"), List.of(new float[]{1, 0})));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM rag_chunks", Integer.class));
    }

    @Test
    void expiredLeaseCanBeReclaimedButOldWorkerCannotCommit() {
        jobs.reconcile(MODEL, 2, 1);
        var old = jobs.claim(MODEL, 2).orElseThrow();
        assertTrue(jobs.claim(MODEL, 2).isEmpty());
        jdbc.update("UPDATE rag_index_jobs SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second'");
        var current = jobs.claim(MODEL, 2).orElseThrow();
        assertNotEquals(old.leaseToken(), current.leaseToken());
        assertFalse(jobs.complete(old, List.of("old"), List.of(new float[]{1, 0})));
        assertTrue(jobs.complete(current, List.of("current"), List.of(new float[]{1, 0})));
    }

    @Test
    void sourceChangingDuringEmbeddingCannotCommit() {
        jobs.reconcile(MODEL, 2, 1);
        var job = jobs.claim(MODEL, 2).orElseThrow();
        jdbc.update("UPDATE documents SET updated_at = updated_at + INTERVAL '1 second' WHERE id = 1");
        assertFalse(jobs.complete(job, List.of("stale"), List.of(new float[]{1, 0})));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM rag_chunks", Integer.class));
    }

    @Test
    void fiveFailuresStopRetryingAndRetainOnlySafeErrorCode() {
        jobs.reconcile(MODEL, 2, 1);
        for (int attempt = 0; attempt < 5; attempt++) {
            var job = jobs.claim(MODEL, 2).orElseThrow();
            jobs.fail(job, "INDEXING_FAILED");
            jdbc.update("UPDATE rag_index_jobs SET available_at = CURRENT_TIMESTAMP");
        }
        assertTrue(jobs.claim(MODEL, 2).isEmpty());
        assertEquals("FAILED", jdbc.queryForObject("SELECT state FROM rag_index_jobs", String.class));
    }

    @Test
    void dailyBudgetIsPersistedAndCannotOverspend() {
        assertTrue(jobs.reserveEmbeddingCharacters(6, 10));
        assertFalse(jobs.reserveEmbeddingCharacters(5, 10));
        assertTrue(jobs.reserveEmbeddingCharacters(4, 10));
        assertFalse(jobs.reserveEmbeddingCharacters(1, 10));
        assertEquals(10, jdbc.queryForObject("SELECT used_characters FROM rag_embedding_budget", Long.class));
    }

    @Test
    void modelAndDimensionChangesBackfillIndependentlyAndHardDeleteCascadesAll() {
        completeFirstJob();
        jobs.reconcile(MODEL, 3, 1);
        var resized = jobs.claim(MODEL, 3).orElseThrow();
        assertTrue(jobs.complete(resized, List.of("new dimensions"), List.of(new float[]{1, 0, 0})));
        jobs.reconcile("replacement-model", 2, 1);
        var replacement = jobs.claim("replacement-model", 2).orElseThrow();
        assertTrue(jobs.complete(replacement, List.of("new model"), List.of(new float[]{1, 0})));
        assertEquals("new dimensions", index.search(new float[]{1, 0, 0}, MODEL, List.of(1L), 5, 0).get(0).text());
        assertEquals("new model", index.search(new float[]{1, 0}, "replacement-model", List.of(1L), 5, 0).get(0).text());
        // Same order as the application's hard_delete_document function.
        jdbc.update("DELETE FROM versions WHERE document_id = 1");
        jdbc.update("DELETE FROM documents WHERE id = 1");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM rag_chunks WHERE document_id = 1", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM rag_index_jobs WHERE document_id = 1", Integer.class));
    }

    @Test
    void concurrentReservationsAndClaimsCannotDoubleSpendOrDuplicateWorkers() throws Exception {
        jobs.reconcile(MODEL, 2, 1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start = new java.util.concurrent.CountDownLatch(1);
            var first = pool.submit(() -> { start.await(); return jobs.reserveEmbeddingCharacters(7, 10); });
            var second = pool.submit(() -> { start.await(); return jobs.reserveEmbeddingCharacters(7, 10); });
            start.countDown();
            assertNotEquals(first.get(), second.get());
            var claimStart = new java.util.concurrent.CountDownLatch(1);
            var claim1 = pool.submit(() -> { claimStart.await(); return jobs.claim(MODEL, 2).isPresent(); });
            var claim2 = pool.submit(() -> { claimStart.await(); return jobs.claim(MODEL, 2).isPresent(); });
            claimStart.countDown();
            assertNotEquals(claim1.get(), claim2.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void firstPublishedDocumentGetsImmutableGitBaselineAndCanBeIndexed() throws Exception {
        jdbc.update("DELETE FROM versions WHERE document_id = 1");
        Path directory = Files.createTempDirectory("rag-baseline-test-");
        try {
            try (Git git = Git.init().setDirectory(directory.toFile()).call()) { }
            var content = new JGitDocumentContentRepository(directory.toString());
            content.saveContent("spaces/1.md", "Первичная публикация", "Create", "author", "author@example.test");
            DocumentVersionRepository versions = mock(DocumentVersionRepository.class);
            when(versions.save(any())).thenAnswer(call -> {
                DocumentVersion version = call.getArgument(0);
                jdbc.update("INSERT INTO versions (document_id, git_hash, git_file_path, created_at, author_id, comment) VALUES (?, ?, ?, ?, ?, ?)",
                        version.documentId(), version.gitHash(), version.gitFilePath(), version.createdAt(), version.authorId(), version.comment());
                return version;
            });
            var baselines = new RagDocumentBaselineService(jdbc, new DataSourceTransactionManager(dataSource),
                    content, versions, new ObjectMapper());
            baselines.baselinePublished(10);
            baselines.baselinePublished(10);
            String sha = jdbc.queryForObject("SELECT git_hash FROM versions WHERE document_id = 1", String.class);
            assertEquals("Первичная публикация", content.readDocumentVersion("spaces/1.md", sha).orElseThrow());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM versions WHERE document_id = 1", Integer.class));
            jobs.reconcile(MODEL, 2, 1);
            assertEquals(sha, jobs.claim(MODEL, 2).orElseThrow().versionSha());
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void failedBaselineDatabaseWriteReusesGitSnapshotWithUtcSecondPrecision() throws Exception {
        jdbc.update("DELETE FROM versions WHERE document_id = 1");
        Path directory = Files.createTempDirectory("rag-baseline-retry-");
        try {
            try (Git git = Git.init().setDirectory(directory.toFile()).call()) { }
            var content = new JGitDocumentContentRepository(directory.toString());
            content.saveContent("spaces/1.md", "Первичная публикация", "Create", "author", "author@example.test");
            DocumentVersionRepository versions = mock(DocumentVersionRepository.class);
            var failFirst = new java.util.concurrent.atomic.AtomicBoolean(true);
            var savedVersion = new java.util.concurrent.atomic.AtomicReference<DocumentVersion>();
            when(versions.save(any())).thenAnswer(call -> {
                if (failFirst.getAndSet(false)) throw new IllegalStateException("Simulated database failure");
                DocumentVersion version = call.getArgument(0);
                savedVersion.set(version);
                jdbc.update("INSERT INTO versions (document_id, git_hash, git_file_path, created_at) VALUES (?, ?, ?, ?)",
                        version.documentId(), version.gitHash(), version.gitFilePath(), version.createdAt());
                return version;
            });
            var baselines = new RagDocumentBaselineService(jdbc, new DataSourceTransactionManager(dataSource),
                    content, versions, new ObjectMapper());
            baselines.baselinePublished(10);
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM versions WHERE document_id = 1", Integer.class));
            String firstSnapshot = content.latestCommitForPath(".metadata/rag-baselines/1.json").orElseThrow();
            baselines.baselinePublished(10); // Cursor wraps after exhausting the bounded scan.
            baselines.baselinePublished(10);
            assertEquals(firstSnapshot, savedVersion.get().gitHash());
            assertEquals(0, savedVersion.get().createdAt().getNano());
            assertTrue(Math.abs(java.time.Duration.between(savedVersion.get().createdAt(),
                    LocalDateTime.now(java.time.ZoneOffset.UTC)).getSeconds()) < 10);
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM versions WHERE document_id = 1", Integer.class));
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
