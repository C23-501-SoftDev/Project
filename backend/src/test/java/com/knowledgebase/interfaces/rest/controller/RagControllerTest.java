package com.knowledgebase.interfaces.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebase.application.service.RagAnswerService;
import com.knowledgebase.application.service.SpaceService;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.rag.RagAnswer;
import com.knowledgebase.domain.rag.RagException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RagControllerTest {
    private RagAnswerService service;
    private MockMvc mvc;
    private User user;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = mock(RagAnswerService.class);
        user = mock(User.class);
        when(user.getId()).thenReturn(17L);
        when(user.isAdmin()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new RagController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsAvailabilityWithoutCaching() throws Exception {
        mvc.perform(get("/api/ai/rag/status"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void usesAuthenticatedIdentityAndReturnsVersionedSources() throws Exception {
        when(service.ask(17L, false, "Как сохранить статью?", List.of(3L)))
                .thenReturn(new RagAnswer("Нажмите «Сохранить» [1].",
                        List.of(new RagAnswer.Source(42, "Публикация", "/documents/42", "sha-42", 0)), false, "request-1"));
        mvc.perform(post("/api/ai/ask").contentType(APPLICATION_JSON)
                        .content("{\"question\":\"  Как сохранить статью?  \",\"spaceIds\":[3],\"userId\":999,\"isAdmin\":true}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.answer").value("Нажмите «Сохранить» [1]."))
                .andExpect(jsonPath("$.sources[0].documentId").value(42))
                .andExpect(jsonPath("$.sources[0].versionSha").value("sha-42"))
                .andExpect(jsonPath("$.insufficientEvidence").value(false));
        verify(service).ask(17L, false, "Как сохранить статью?", List.of(3L));
    }

    @Test
    void rejectsBlankOversizedAndMissingQuestionsBeforeService() throws Exception {
        for (String body : List.of("{}", "{\"question\":null}", "{\"question\":\"   \"}",
                json.writeValueAsString(Map.of("question", "x".repeat(2001))))) {
            mvc.perform(post("/api/ai/ask").contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidSpaceIdsAndTooManySpacesBeforeService() throws Exception {
        List<List<Long>> selections = List.of(List.of(0L), List.of(-1L), Arrays.asList(1L, null),
                LongStream.rangeClosed(1, 21).boxed().toList());
        for (List<Long> ids : selections) {
            mvc.perform(post("/api/ai/ask").contentType(APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("question", "Вопрос", "spaceIds", ids))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void malformedJsonHasControlledResponseWithoutEchoingContent() throws Exception {
        mvc.perform(post("/api/ai/ask").contentType(APPLICATION_JSON).content("{\"question\": private-data"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-data"))));
        verifyNoInteractions(service);
    }

    @Test
    void mapsAllServiceErrorsWithoutDisclosingProviderDetails() throws Exception {
        Map<RagException.Code, Integer> statuses = Map.of(
                RagException.Code.DISABLED, 503, RagException.Code.INVALID_REQUEST, 400,
                RagException.Code.RATE_LIMITED, 429, RagException.Code.PROVIDER_UNAVAILABLE, 502,
                RagException.Code.SOURCES_CHANGED, 409, RagException.Code.INDEX_UNAVAILABLE, 503);
        for (var item : statuses.entrySet()) {
            RagException failure = mock(RagException.class);
            when(failure.getCode()).thenReturn(item.getKey());
            when(failure.getMessage()).thenReturn("secret-api-key private-document");
            when(service.ask(anyLong(), anyBoolean(), anyString(), isNull())).thenThrow(failure);
            mvc.perform(post("/api/ai/ask").contentType(APPLICATION_JSON).content("{\"question\":\"Вопрос\"}"))
                    .andExpect(status().is(item.getValue()))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value(item.getKey().name()))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-api-key"))));
            reset(service);
        }
    }

    @Test
    void pageLoadsOnlyAccessibleSpacesWithoutDocumentTrees() {
        SpaceService spaces = mock(SpaceService.class);
        when(spaces.getSpacesForUser(17L, false)).thenReturn(List.of());
        var model = new ExtendedModelMap();
        assertEquals("layout", new RagPageController(spaces).ask(user, model));
        assertEquals("rag/ask", model.get("content"));
        assertEquals(user, model.get("currentUser"));
        assertFalse(model.containsKey("spaceTrees"));
        verify(spaces).getSpacesForUser(17L, false);
    }
}
