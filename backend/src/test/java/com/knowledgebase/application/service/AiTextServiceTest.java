package com.knowledgebase.application.service;

import com.knowledgebase.infrastructure.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiTextServiceTest {

    private AiProperties properties;
    private MockRestServiceServer server;
    private AiTextService service;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://routerai.test/api/v1");
        properties.setModel("test-model");

        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        service = new AiTextService(builder.build(), properties);
    }

    @Test
    void transform_withFixedAction_sendsActionInstruction() {
        server.expect(requestTo("https://routerai.test/api/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().string(containsString("test-model")))
                .andExpect(content().string(containsString("официально-деловом стиле")))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"Официальный текст"}}]}
                        """, MediaType.APPLICATION_JSON));

        String result = service.transform("исходный текст", "formal", null);

        assertThat(result, is("Официальный текст"));
        server.verify();
    }

    @Test
    void transform_withCustomPrompt_sendsCustomInstruction() {
        server.expect(requestTo("https://routerai.test/api/v1/chat/completions"))
                .andExpect(content().string(containsString("Пиши как чек-лист")))
                .andExpect(content().string(containsString("Выполни пользовательскую инструкцию")))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"- Шаг 1\\n- Шаг 2"}}]}
                        """, MediaType.APPLICATION_JSON));

        String result = service.transform("исходный текст", null, "Пиши как чек-лист");

        assertThat(result, is("- Шаг 1\n- Шаг 2"));
        server.verify();
    }

    @Test
    void transform_withBothActionAndPrompt_rejectsBeforeProviderCall() {
        assertThrows(IllegalArgumentException.class,
                () -> service.transform("текст", "formal", "Сделай иначе"));
    }

    @Test
    void transform_withBlankPrompt_rejectsBeforeProviderCall() {
        assertThrows(IllegalArgumentException.class,
                () -> service.transform("текст", null, " "));
    }

    @Test
    void transform_whenProviderFails_wrapsException() {
        server.expect(requestTo("https://routerai.test/api/v1/chat/completions"))
                .andRespond(withServerError());

        assertThrows(AiTextService.AiServiceException.class,
                () -> service.transform("текст", "formal", null));
        server.verify();
    }
}
