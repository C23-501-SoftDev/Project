package com.knowledgebase.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebase.domain.rag.RagGenerationClient.Context;
import com.knowledgebase.domain.rag.RagProviderException;
import com.knowledgebase.infrastructure.config.RagConfig;
import com.knowledgebase.infrastructure.config.RagProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OllamaRagClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private ExecutorService executor;
    private RagProperties properties;
    private OllamaRagClient client;
    private final AtomicReference<String> response = new AtomicReference<>();
    private final AtomicReference<JsonNode> request = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int status = 200;
    private volatile long delayMillis;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try {
                calls.incrementAndGet();
                request.set(mapper.readTree(exchange.getRequestBody()));
                path.set(exchange.getRequestURI().getPath());
                if (delayMillis > 0) Thread.sleep(delayMillis);
                if (status == 302) exchange.getResponseHeaders().add("Location", "http://example.com/private");
                byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        properties = new RagProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setEmbeddingDimensions(3);
        client = new RagConfig().ollamaRagClient(properties, mapper);
        response.set("{\"model\":\"qwen3-embedding:0.6b\",\"embeddings\":[[1,0.5,-0.25]]}");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void nativeEmbeddingsPreserveOrderAndDisableTruncation() {
        response.set("{\"model\":\"qwen3-embedding:0.6b\",\"embeddings\":[[1,2,3],[-1,0,1]]}");
        List<float[]> vectors = client.embed(List.of("Первый", "Второй"));
        assertArrayEquals(new float[]{1,2,3}, vectors.get(0));
        assertArrayEquals(new float[]{-1,0,1}, vectors.get(1));
        assertEquals("/api/embed", path.get());
        assertEquals("qwen3-embedding:0.6b", request.get().path("model").asText());
        assertEquals("Второй", request.get().path("input").get(1).asText());
        assertFalse(request.get().path("truncate").asBoolean(true));
    }

    @Test
    void rejectsAnUnexpectedEmbeddingModel() {
        response.set("{\"model\":\"other-model\",\"embeddings\":[[1,2,3]]}");
        assertThrows(RagProviderException.class, () -> client.embed(List.of("text")));
    }

    @Test
    void disabledOrNonlocalConfigurationMakesNoNetworkRequest() {
        properties.setEnabled(false);
        assertEquals(RagProviderException.Code.DISABLED, assertThrows(RagProviderException.class,
                () -> client.embed(List.of("private"))).getCode());
        properties.setEnabled(true);
        properties.setBaseUrl("https://example.com");
        assertThrows(RagProviderException.class, () -> client.generate("Question", contexts()));
        assertEquals(0, calls.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"embeddings\":[]}", "{\"embeddings\":[[1,2]]}",
            "{\"embeddings\":[[0,0,0]]}", "{\"embeddings\":[[1,\"2\",3]]}",
            "{\"embeddings\":[[1,1e99,3]]}", "{\"embeddings\":[[1,2,3],[1,2,3]]}",
            "{\"embeddings\":[[1,2,3]],\"embeddings\":[[1,2,3]]}",
            "{\"embeddings\":[[1,2,3]]} {\"injected\":true}", "private malformed provider data"})
    void rejectsMalformedEmbeddingResponsesWithoutLeakingBody(String body) {
        response.set(body.startsWith("{")
                ? "{\"model\":\"qwen3-embedding:0.6b\"," + body.substring(1) : body);
        RagProviderException error = assertThrows(RagProviderException.class, () -> client.embed(List.of("private")));
        assertEquals(RagProviderException.Code.INVALID_RESPONSE, error.getCode());
        assertFalse(error.getMessage().contains("private"));
        assertNull(error.getCause());
    }

    @Test
    void boundedNativeGenerationUsesSchemaAndUntrustedSourceInstruction() throws Exception {
        response.set(chat("{\"answer\":\"Политика требует 30 дней.\",\"sourceIds\":[1],\"insufficientEvidence\":false}"));
        var answer = client.generate("Каков срок?", contexts());
        assertEquals(List.of(1), answer.sourceIds());
        assertFalse(answer.insufficientEvidence());
        assertEquals("/api/chat", path.get());
        assertFalse(request.get().path("stream").asBoolean(true));
        assertFalse(request.get().path("think").asBoolean(true));
        assertEquals(600, request.get().path("options").path("num_predict").intValue());
        assertEquals(8192, request.get().path("options").path("num_ctx").intValue());
        assertEquals("object", request.get().path("format").path("type").textValue());
        List<String> fields = new ArrayList<>();
        request.get().path("format").path("properties").fieldNames().forEachRemaining(fields::add);
        assertEquals(List.of("answer", "sourceIds", "insufficientEvidence"), fields);
        assertTrue(request.get().path("messages").get(0).path("content").textValue().contains("недоверенными"));
        JsonNode input = mapper.readTree(request.get().path("messages").get(1).path("content").textValue());
        assertEquals("Каков срок?", input.path("question").textValue());
        assertEquals(1, input.path("sources").get(0).path("sourceId").intValue());
    }

    @Test
    void acceptsExplicitInsufficientEvidence() throws Exception {
        response.set(chat("{\"answer\":\"Недостаточно информации.\",\"sourceIds\":[],\"insufficientEvidence\":true}"));
        assertTrue(client.generate("Вопрос?", contexts()).insufficientEvidence());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"answer\":\"Неизвестный источник\",\"sourceIds\":[9],\"insufficientEvidence\":false}",
            "{\"answer\":\"Дубликат\",\"sourceIds\":[1,1],\"insufficientEvidence\":false}",
            "{\"answer\":\"Подмена типа\",\"sourceIds\":[\"1\"],\"insufficientEvidence\":false}",
            "{\"answer\":\"Без доказательств\",\"sourceIds\":[],\"insufficientEvidence\":false}",
            "{\"answer\":\"Несогласованный ответ\",\"sourceIds\":[1],\"insufficientEvidence\":true}",
            "{\"answer\":\"Лишнее поле\",\"sourceIds\":[1],\"insufficientEvidence\":false,\"url\":\"https://evil.test\"}",
            "{\"answer\":\"\",\"sourceIds\":[1],\"insufficientEvidence\":false}",
            "{\"answer\":\"a\",\"sourceIds\":[1],\"insufficientEvidence\":\"false\"}",
            "```json\n{}\n```"})
    void rejectsMalformedOrInventedCitations(String content) throws Exception {
        response.set(chat(content));
        assertEquals(RagProviderException.Code.INVALID_RESPONSE, assertThrows(RagProviderException.class,
                () -> client.generate("Question", contexts())).getCode());
    }

    @Test
    void rejectsUnfinishedAndTruncatedGeneration() throws Exception {
        response.set("{\"model\":\"qwen3:4b\",\"done\":false,\"message\":{\"content\":\"{}\"}}");
        assertThrows(RagProviderException.class, () -> client.generate("Question", contexts()));
        response.set(chat("{}").replace("\"stop\"", "\"length\""));
        assertThrows(RagProviderException.class, () -> client.generate("Question", contexts()));
    }

    @Test
    void rejectsTooLargeInputsBeforeNetwork() {
        assertThrows(IllegalArgumentException.class, () -> client.embed(List.of("x".repeat(properties.getMaxDocumentChars() + 1))));
        assertThrows(IllegalArgumentException.class, () -> client.generate("Question",
                List.of(new Context(1, "title", "x".repeat(properties.getMaxContextChars())))));
        assertEquals(0, calls.get());
    }

    @Test
    void providerErrorsAndRedirectsAreControlledWithoutFollowingRedirect() {
        status = 500;
        response.set("private provider detail with secret key");
        RagProviderException error = assertThrows(RagProviderException.class, () -> client.embed(List.of("text")));
        assertEquals(RagProviderException.Code.UNAVAILABLE, error.getCode());
        assertFalse(error.getMessage().contains("secret"));
        assertNull(error.getCause());
        status = 302;
        assertThrows(RagProviderException.class, () -> client.embed(List.of("text")));
        assertEquals(2, calls.get());
    }

    @Test
    void totalRequestDeadlineIsBounded() {
        properties.setTimeoutSeconds(1);
        delayMillis = 3000;
        assertTimeoutPreemptively(Duration.ofMillis(2300), () -> {
            RagProviderException error = assertThrows(RagProviderException.class, () -> client.embed(List.of("text")));
            assertEquals(RagProviderException.Code.UNAVAILABLE, error.getCode());
        });
    }

    @Test
    void oversizedResponseIsStoppedBeforeParsing() {
        response.set(" ".repeat(8 * 1024 * 1024 + 1));
        assertThrows(RagProviderException.class, () -> client.embed(List.of("text")));
    }

    private String chat(String content) throws Exception {
        return mapper.writeValueAsString(Map.of("model", properties.getGenerationModel(), "done", true, "done_reason", "stop",
                "message", Map.of("role", "assistant", "content", content)));
    }

    private List<Context> contexts() { return List.of(new Context(1, "Политика", "Срок хранения — 30 дней.")); }
}
