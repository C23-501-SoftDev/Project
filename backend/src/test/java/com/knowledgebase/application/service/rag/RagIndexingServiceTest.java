package com.knowledgebase.application.service.rag;

import com.knowledgebase.domain.rag.RagEmbeddingClient;
import com.knowledgebase.domain.repository.DocumentContentRepository;
import com.knowledgebase.domain.repository.RagIndexRepository;
import com.knowledgebase.infrastructure.config.RagProperties;
import com.knowledgebase.infrastructure.persistence.rag.RagIndexJob;
import com.knowledgebase.infrastructure.persistence.rag.RagIndexJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagIndexingServiceTest {
    private final RagProperties properties = new RagProperties();
    private final RagIndexRepository index = mock(RagIndexRepository.class);
    private final RagIndexJobRepository jobs = mock(RagIndexJobRepository.class);
    private final DocumentContentRepository content = mock(DocumentContentRepository.class);
    private final RagEmbeddingClient embeddings = mock(RagEmbeddingClient.class);
    private final RagDocumentBaselineService baselines = mock(RagDocumentBaselineService.class);
    private final RagIndexingService service = new RagIndexingService(properties, index, jobs, content, embeddings, baselines);
    private final RagIndexJob job = new RagIndexJob(1, 2, "a".repeat(40), "spaces/example.md",
            "test-model", 2, "lease", LocalDateTime.of(2026, 10, 26, 1, 0));

    @BeforeEach
    void prepare() {
        properties.setEmbeddingModel("test-model");
        properties.setEmbeddingDimensions(2);
        properties.setIndexingBatchSize(1);
        when(index.isAvailable()).thenReturn(true);
        when(jobs.claim(anyString(), anyInt())).thenReturn(Optional.of(job));
        when(jobs.renewLease(job)).thenReturn(true);
        when(jobs.reserveEmbeddingCharacters(anyLong(), anyLong())).thenReturn(true);
        when(content.readDocumentVersion(job.gitFilePath(), job.versionSha())).thenReturn(Optional.of("Проверенная статья"));
        when(embeddings.embed(anyList())).thenReturn(List.of(new float[]{1, 0}));
    }

    @Test
    void disabledDoesNotTouchDatabaseOrProvider() {
        service.runOnce();
        verifyNoInteractions(index, jobs, content, embeddings, baselines);
    }

    @Test
    void immutableVersionIsIndexedAndReservedBeforeProviderCall() {
        properties.setEnabled(true);
        service.runOnce();
        var order = inOrder(jobs, content, embeddings);
        order.verify(jobs).reconcile("test-model", 2, 10);
        order.verify(jobs).claim("test-model", 2);
        order.verify(content).readDocumentVersion(job.gitFilePath(), job.versionSha());
        order.verify(jobs).reserveEmbeddingCharacters("Проверенная статья".length(), 1000000);
        order.verify(jobs).renewLease(job);
        order.verify(embeddings).embed(List.of("Проверенная статья"));
        verify(jobs).complete(eq(job), eq(List.of("Проверенная статья")), anyList());
    }

    @Test
    void exhaustedBudgetDefersWithoutCallingProvider() {
        properties.setEnabled(true);
        when(jobs.reserveEmbeddingCharacters(anyLong(), anyLong())).thenReturn(false);
        service.runOnce();
        verify(jobs).deferForBudget(job);
        verifyNoInteractions(embeddings);
        verify(jobs, never()).complete(any(), anyList(), anyList());
    }

    @Test
    void providerFailureIsRetryableAndDoesNotEscapeToEditing() {
        properties.setEnabled(true);
        when(embeddings.embed(anyList())).thenThrow(new IllegalStateException("sensitive provider body"));
        assertDoesNotThrow(service::runOnce);
        verify(jobs).fail(job, "INDEXING_FAILED");
        verify(jobs, never()).complete(any(), anyList(), anyList());
    }

    @Test
    void expiredLeaseNeverCallsProviderOrCommits() {
        properties.setEnabled(true);
        when(jobs.renewLease(job)).thenReturn(false);
        service.runOnce();
        verifyNoInteractions(embeddings);
        verify(jobs, never()).complete(any(), anyList(), anyList());
    }

    @Test
    void overlappingChunksAreStableBoundedAndDoNotSplitSurrogates() {
        String markdown = "Я".repeat(99) + "😀" + "Б".repeat(120);
        List<String> chunks = RagIndexingService.chunk(markdown, 100, 10);
        assertEquals(chunks, RagIndexingService.chunk(markdown, 100, 10));
        assertEquals(3, chunks.size());
        assertTrue(chunks.stream().allMatch(value -> value.length() <= 100));
        assertTrue(chunks.stream().noneMatch(value -> Character.isLowSurrogate(value.charAt(0))
                || Character.isHighSurrogate(value.charAt(value.length() - 1))));
        assertTrue(RagIndexingService.chunk("   ", 100, 10).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> RagIndexingService.chunk("abc", 100, 100));
    }

    @Test
    void maximumOverlapAlwaysAdvancesPastSurrogateBoundary() {
        String markdown = "Я".repeat(127) + "😀" + "Б".repeat(130);
        List<String> chunks = assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),
                () -> RagIndexingService.chunk(markdown, 128, 127));
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() <= markdown.length());
        assertTrue(chunks.stream().allMatch(value -> value.length() <= 128));
        assertTrue(chunks.stream().noneMatch(value -> Character.isLowSurrogate(value.charAt(0))
                || Character.isHighSurrogate(value.charAt(value.length() - 1))));
        assertTrue(chunks.stream().anyMatch(value -> value.contains("😀")));
        assertEquals(markdown.substring(markdown.length() - 128), chunks.get(chunks.size() - 1));
    }
}
