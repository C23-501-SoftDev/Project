package com.knowledgebase.application.service.rag;

import com.knowledgebase.domain.rag.RagEmbeddingClient;
import com.knowledgebase.domain.repository.DocumentContentRepository;
import com.knowledgebase.domain.repository.RagIndexRepository;
import com.knowledgebase.infrastructure.config.RagProperties;
import com.knowledgebase.infrastructure.persistence.rag.RagIndexJob;
import com.knowledgebase.infrastructure.persistence.rag.RagIndexJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Polling the committed SQL state is also a durable backfill/reconciliation mechanism. */
@Service
public class RagIndexingService {
    private static final Logger log = LoggerFactory.getLogger(RagIndexingService.class);
    private final RagProperties properties;
    private final RagIndexRepository index;
    private final RagIndexJobRepository jobs;
    private final DocumentContentRepository content;
    private final RagEmbeddingClient embeddings;
    private final RagDocumentBaselineService baselines;

    public RagIndexingService(RagProperties properties, RagIndexRepository index, RagIndexJobRepository jobs,
                              DocumentContentRepository content, RagEmbeddingClient embeddings,
                              RagDocumentBaselineService baselines) {
        this.properties = properties;
        this.index = index;
        this.jobs = jobs;
        this.content = content;
        this.embeddings = embeddings;
        this.baselines = baselines;
    }

    @Scheduled(fixedDelayString = "${app.rag.indexing-delay-ms:15000}",
               initialDelayString = "${app.rag.indexing-initial-delay-ms:15000}")
    public void runOnce() {
        if (!properties.isConfigured() || !properties.isIndexingEnabled()) return;
        try {
            if (!index.isAvailable()) return;
            int batchSize = Math.max(1, Math.min(properties.getIndexingBatchSize(), 100));
            baselines.baselinePublished(batchSize);
            jobs.reconcile(properties.getEmbeddingModel(), properties.getEmbeddingDimensions(), batchSize * 10);
            for (int i = 0; i < batchSize; i++) {
                var next = jobs.claim(properties.getEmbeddingModel(), properties.getEmbeddingDimensions());
                if (next.isEmpty()) break;
                process(next.get());
            }
        } catch (RuntimeException ex) {
            // No content/question/provider response is logged, and editing never depends on the worker.
            log.warn("RAG indexing pass unavailable: {}", ex.getClass().getSimpleName());
        }
    }

    private void process(RagIndexJob job) {
        try {
            String markdown = content.readDocumentVersion(job.gitFilePath(), job.versionSha())
                    .orElseThrow(() -> new IllegalStateException("Missing immutable source"));
            if (markdown.length() > properties.getMaxDocumentChars()) {
                jobs.fail(job, "DOCUMENT_TOO_LARGE");
                return;
            }
            List<String> chunks = chunk(markdown, properties.getChunkChars(), properties.getChunkOverlap());
            long characters = chunks.stream().mapToLong(String::length).sum();
            if (characters > 0 && !jobs.reserveEmbeddingCharacters(characters, properties.getDailyEmbeddingCharacters())) {
                jobs.deferForBudget(job);
                return;
            }
            List<float[]> vectors = new ArrayList<>();
            for (int offset = 0; offset < chunks.size(); offset += 10) {
                if (!jobs.renewLease(job)) return;
                List<String> batch = chunks.subList(offset, Math.min(offset + 10, chunks.size()));
                List<float[]> result = embeddings.embed(batch);
                if (result.size() != batch.size()) throw new IllegalStateException("Embedding count differs");
                vectors.addAll(result);
            }
            if (jobs.complete(job, chunks, vectors)) {
                log.info("RAG indexed documentId={} version={} chunks={} model={}",
                        job.documentId(), job.versionSha(), chunks.size(), job.embeddingModel());
            }
        } catch (RuntimeException ex) {
            jobs.fail(job, "INDEXING_FAILED");
            log.warn("RAG indexing failed documentId={} error={}", job.documentId(), ex.getClass().getSimpleName());
        }
    }

    /** Deterministic overlapping chunks; preserve text instead of interpreting Markdown instructions. */
    static List<String> chunk(String markdown, int size, int overlap) {
        if (size < 100 || overlap < 0 || overlap >= size) throw new IllegalArgumentException("Invalid chunk bounds");
        if (markdown == null || markdown.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < markdown.length()) {
            int end = Math.min(start + size, markdown.length());
            if (end < markdown.length() && Character.isHighSurrogate(markdown.charAt(end - 1))) end--;
            String text = markdown.substring(start, end);
            if (!text.isBlank()) result.add(text);
            if (end == markdown.length()) break;
            // A surrogate-safe shortened end must still advance with maximum configured overlap.
            start = Math.max(start + 1, end - overlap);
            if (start > 0 && Character.isLowSurrogate(markdown.charAt(start))) start++;
        }
        return List.copyOf(result);
    }
}
