package com.knowledgebase.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebase.infrastructure.ai.OllamaRagClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Configuration
@org.springframework.scheduling.annotation.EnableScheduling
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {
    @Bean
    public OllamaRagClient ollamaRagClient(RagProperties properties, ObjectMapper objectMapper) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, Math.min(120, properties.getTimeoutSeconds()))))
                .followRedirects(HttpClient.Redirect.NEVER)
                // Local source excerpts must not be routed through a system/network proxy.
                .proxy(new ProxySelector() {
                    @Override public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
                    @Override public void connectFailed(URI uri, SocketAddress address, java.io.IOException ex) { }
                }).build();
        return new OllamaRagClient(properties, objectMapper, client);
    }
}
