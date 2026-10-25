package com.knowledgebase.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class RagPropertiesTest {
    @Test
    void rejectsExcessiveOverlapToBoundIndexingMemory() {
        RagProperties properties = new RagProperties();
        properties.setEnabled(true);
        properties.setChunkChars(16000);
        properties.setChunkOverlap(15999);
        assertFalse(properties.isConfigured());
        properties.setChunkOverlap(8000);
        assertTrue(properties.isConfigured());
    }

    @Test
    void defaultsAreLocalButOptIn() {
        RagProperties p = new RagProperties();
        assertFalse(p.isConfigured());
        assertEquals("http://127.0.0.1:11434", p.getBaseUrl());
        assertEquals(1024, p.getEmbeddingDimensions());
        assertEquals(8, p.getTopK());
        assertEquals(2, p.getMaxChunksPerDocument());
        p.setEnabled(true);
        assertTrue(p.isConfigured());
    }

    @Test
    void perDocumentLimitIsBoundedIndependentlyOfGlobalLimit() {
        RagProperties p = new RagProperties();
        p.setEnabled(true);
        p.setTopK(1);
        assertTrue(p.isConfigured()); // A global limit below the per-document cap is valid.
        for (int value : new int[]{0, -1, 21, Integer.MAX_VALUE}) {
            p.setMaxChunksPerDocument(value);
            assertFalse(p.isConfigured());
        }
        for (int value : new int[]{1, 2, 20}) {
            p.setMaxChunksPerDocument(value);
            assertTrue(p.isConfigured());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com", "https://ollama.com", "http://192.168.1.1:11434",
            "http://localhost.example.com", "http://user@localhost:11434", "http://127.0.0.1:11434?key=private",
            "http://127.0.0.1:11434#fragment", "http://127.999.0.1", "file:///tmp/api", "http://localhost:99999"})
    void refusesNonlocalOrAmbiguousEndpoints(String endpoint) {
        RagProperties p = new RagProperties();
        p.setEnabled(true);
        p.setBaseUrl(endpoint);
        assertFalse(p.isConfigured());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1:11434", "http://localhost:11434", "http://[::1]:11434",
            "http://ollama:11434", "http://host.docker.internal:11434"})
    void acceptsExplicitLocalDeploymentEndpoints(String endpoint) {
        RagProperties p = new RagProperties();
        p.setEnabled(true);
        p.setBaseUrl(endpoint);
        assertTrue(p.isConfigured());
    }

    @Test
    void rejectsInvalidBudgetDimensionAndTimeoutSettings() {
        RagProperties p = new RagProperties();
        p.setEnabled(true);
        p.setEmbeddingDimensions(0);
        assertFalse(p.isConfigured());
        p.setEmbeddingDimensions(1024);
        p.setMinimumScore(Double.NaN);
        assertFalse(p.isConfigured());
        p.setMinimumScore(0.3);
        p.setTimeoutSeconds(121);
        assertFalse(p.isConfigured());
        p.setTimeoutSeconds(90);
        p.setChunkOverlap(p.getChunkChars());
        assertFalse(p.isConfigured());
        p.setChunkOverlap(150);
        p.setDailyEmbeddingCharacters(0);
        assertFalse(p.isConfigured());
        p.setDailyEmbeddingCharacters(1000000);
        p.setGenerationModel("qwen3:cloud");
        assertFalse(p.isConfigured());
    }
}
