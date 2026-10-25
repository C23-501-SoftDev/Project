package com.knowledgebase.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/** Local Ollama RAG settings. No remote endpoints or credentials are used. */
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {
    private boolean enabled = false;
    private boolean servingEnabled = true;
    private String baseUrl = "http://127.0.0.1:11434";
    private String embeddingModel = "qwen3-embedding:0.6b";
    private String generationModel = "qwen3:4b";
    private int embeddingDimensions = 1024;
    private int timeoutSeconds = 90;
    private int maxOutputTokens = 600;
    private int generationContextTokens = 8192;
    private int maxContextChars = 12000;
    private int chunkChars = 1200;
    private int chunkOverlap = 150;
    private int topK = 5;
    private double minimumScore = 0.3;
    private int requestsPerMinute = 6;
    private int dailyRequests = 100;
    private long dailyEmbeddingCharacters = 1000000;
    private int indexingBatchSize = 10;
    private int maxDocumentChars = 100000;
    private boolean indexingEnabled = true;

    public boolean isServingEnabled() { return servingEnabled; }
    public void setServingEnabled(boolean value) { servingEnabled = value; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }

    public String getGenerationModel() { return generationModel; }
    public void setGenerationModel(String generationModel) { this.generationModel = generationModel; }

    public int getEmbeddingDimensions() { return embeddingDimensions; }
    public void setEmbeddingDimensions(int embeddingDimensions) { this.embeddingDimensions = embeddingDimensions; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public int getGenerationContextTokens() { return generationContextTokens; }
    public void setGenerationContextTokens(int value) { generationContextTokens = value; }

    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }

    public int getMaxContextChars() { return maxContextChars; }
    public void setMaxContextChars(int maxContextChars) { this.maxContextChars = maxContextChars; }

    public int getChunkChars() { return chunkChars; }
    public void setChunkChars(int chunkChars) { this.chunkChars = chunkChars; }

    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }

    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }

    public double getMinimumScore() { return minimumScore; }
    public void setMinimumScore(double minimumScore) { this.minimumScore = minimumScore; }

    public int getRequestsPerMinute() { return requestsPerMinute; }
    public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }

    public int getDailyRequests() { return dailyRequests; }
    public void setDailyRequests(int dailyRequests) { this.dailyRequests = dailyRequests; }

    public long getDailyEmbeddingCharacters() { return dailyEmbeddingCharacters; }
    public void setDailyEmbeddingCharacters(long dailyEmbeddingCharacters) { this.dailyEmbeddingCharacters = dailyEmbeddingCharacters; }

    public int getIndexingBatchSize() { return indexingBatchSize; }
    public void setIndexingBatchSize(int indexingBatchSize) { this.indexingBatchSize = indexingBatchSize; }

    public int getMaxDocumentChars() { return maxDocumentChars; }
    public void setMaxDocumentChars(int maxDocumentChars) { this.maxDocumentChars = maxDocumentChars; }

    public boolean isIndexingEnabled() { return indexingEnabled; }
    public void setIndexingEnabled(boolean indexingEnabled) { this.indexingEnabled = indexingEnabled; }

    /** Invalid deployment settings fail closed without breaking the rest of the application. */
    public boolean isConfigured() {
        return enabled && validEndpoint(baseUrl)
                && validModel(embeddingModel) && validModel(generationModel)
                && between(embeddingDimensions, 1, 4096) && between(timeoutSeconds, 1, 120)
                && between(generationContextTokens, 2048, 65536) && between(maxOutputTokens, 32, 4096) && between(maxContextChars, 256, 100000)
                && between(chunkChars, 128, 16000) && chunkOverlap >= 0 && chunkOverlap <= chunkChars / 2
                && between(topK, 1, 20) && Double.isFinite(minimumScore) && minimumScore >= -1 && minimumScore <= 1
                && between(requestsPerMinute, 1, 1000) && between(dailyRequests, 1, 100000)
                && dailyEmbeddingCharacters > 0 && dailyEmbeddingCharacters <= 1000000000L
                && between(indexingBatchSize, 1, 100) && between(maxDocumentChars, chunkChars, 1000000);
    }

    private static boolean validEndpoint(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            boolean local = isLoopbackHost(host) || "ollama".equalsIgnoreCase(host)
                    || "host.docker.internal".equalsIgnoreCase(host);
            return local && uri.getUserInfo() == null && uri.getFragment() == null
                    && uri.getQuery() == null && uri.getPort() != 0 && uri.getPort() <= 65535
                    && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException | NullPointerException ex) {
            return false;
        }
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null) return false;
        if (host.equalsIgnoreCase("localhost") || host.equals("[::1]") || host.equals("::1")) return true;
        if (!host.matches("127(?:\\.[0-9]{1,3}){3}")) return false;
        for (String part : host.split("\\.")) if (Integer.parseInt(part) > 255) return false;
        return true;
    }

    private static boolean validModel(String model) {
        return model != null && !model.toLowerCase(java.util.Locale.ROOT).contains("cloud") && model.matches("[A-Za-z0-9][A-Za-z0-9._:/@+\\-]{0,199}");
    }

    private static boolean between(int value, int min, int max) { return value >= min && value <= max; }
}
