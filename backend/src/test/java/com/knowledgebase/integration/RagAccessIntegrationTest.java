package com.knowledgebase.integration;

import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.PermissionType;
import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.model.SpacePermission;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.rag.RagChunk;
import com.knowledgebase.domain.rag.RagGenerationClient;
import com.knowledgebase.domain.repository.DocumentVersionRepository;
import com.knowledgebase.domain.repository.RagIndexRepository;
import com.knowledgebase.domain.repository.SpacePermissionRepository;
import com.knowledgebase.infrastructure.ai.OllamaRagClient;
import com.knowledgebase.infrastructure.config.RagProperties;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Comparator;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual JWT/CSRF, SQL metadata and permission services; only vector/provider boundaries are mocked. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RagAccessIntegrationTest extends IntegrationTestBase {
    @DynamicPropertySource
    static void isolateRagDatabase(DynamicPropertyRegistry registry) {
        // This class has mocked beans and its own create-drop context. Closing it must not
        // drop the schema retained by other cached IntegrationTestBase contexts.
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:mem:rag_access_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
    }

    @MockBean private OllamaRagClient provider;
    @MockBean private RagIndexRepository index;
    @Autowired private RagProperties properties;
    @Autowired private SpacePermissionRepository permissionRepository;
    @Autowired private DocumentVersionRepository versionRepository;

    private User editor;
    private User guest;
    private Space allowedSpace;
    private Document published;
    private String guestJwt;

    @BeforeEach
    void prepareRag() throws Exception {
        properties.setIndexingEnabled(false);
        properties.setServingEnabled(true);
        properties.setEnabled(true);
        when(index.isAvailable()).thenReturn(true);
        float[] embedding = new float[properties.getEmbeddingDimensions()];
        embedding[0] = 1;
        when(provider.embed(anyList())).thenReturn(List.of(embedding));
        when(provider.generate(anyString(), anyList())).thenReturn(
                new RagGenerationClient.GeneratedAnswer("Хранить документы 30 дней.", List.of(1), false));
        editor = persistUser("rag-editor", "password123", "rag-editor@example.test", GlobalRole.EDITOR);
        guest = persistUser("rag-guest", "password123", "rag-guest@example.test", GlobalRole.GUEST);
        allowedSpace = spaceRepository.save(Space.create("RAG allowed", "", editor.getId()));
        published = publish(allowedSpace, "Published policy", "Хранить документы 30 дней.");
        guestJwt = loginAndGetJwt(guest.getLogin(), "password123");
        when(index.search(any(), anyString(), anyList(), anyInt(), anyDouble()))
                .thenReturn(List.of(chunk(published)));
    }

    @AfterEach
    void disableRagAfterTest() {
        properties.setEnabled(false);
        properties.setIndexingEnabled(true);
        properties.setServingEnabled(true);
    }

    @Test
    void actualAuthenticationAndCsrfAreRequiredBeforeAnyModelCall() throws Exception {
        mockMvc.perform(post("/api/ai/ask").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"question\":\"Срок хранения?\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/ai/ask").cookie(jwtCookie(guestJwt)).contentType(APPLICATION_JSON)
                        .content("{\"question\":\"Срок хранения?\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(provider);
    }

    @Test
    void guestWithoutSpaceAccessGetsNoSourceAndNoModelCall() throws Exception {
        ask(guestJwt).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.insufficientEvidence").value(true))
                .andExpect(jsonPath("$.sources", empty()))
                .andExpect(content().string(not(containsString(published.getTitle()))));
        verifyNoInteractions(provider);
        verify(index, never()).search(any(), anyString(), anyList(), anyInt(), anyDouble());
    }

    @Test
    void sqlPermissionsAndPublicationStateRestrictCandidatesAndCitations() throws Exception {
        grant(allowedSpace, guest);
        documentService.createDocument("Private draft", "DRAFT SECRET", allowedSpace.getId(), null, editor.getId(), null);
        Document deleted = publish(allowedSpace, "Deleted policy", "DELETED SECRET");
        documentService.deleteDocument(deleted.getId());
        Space denied = spaceRepository.save(Space.create("RAG inaccessible", "", editor.getId()));
        publish(denied, "Inaccessible policy", "HIDDEN SECRET");
        RagChunk expectedChunk = chunk(published);
        ask(guestJwt).andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence").value(false))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].documentId").value(published.getId()))
                .andExpect(jsonPath("$.sources[0].url").value("/documents/" + published.getId()))
                .andExpect(jsonPath("$.sources[0].versionSha").value(expectedChunk.versionSha()))
                .andExpect(content().string(not(containsString("SECRET"))));
        ArgumentCaptor<List<Long>> allowedIds = ArgumentCaptor.forClass(List.class);
        verify(index).search(any(), anyString(), allowedIds.capture(), anyInt(), anyDouble());
        assertEquals(List.of(published.getId()), allowedIds.getValue());
        ArgumentCaptor<List<RagGenerationClient.Context>> contexts = ArgumentCaptor.forClass(List.class);
        verify(provider).generate(anyString(), contexts.capture());
        assertEquals(1, contexts.getValue().size());
        assertEquals(published.getTitle(), contexts.getValue().get(0).title());
    }

    @Test
    void committedRevocationOfUncitedContextDuringGenerationDiscardsWholeAnswer() throws Exception {
        grant(allowedSpace, guest);
        Space secondSpace = spaceRepository.save(Space.create("RAG second source", "", editor.getId()));
        grant(secondSpace, guest);
        Document second = publish(secondSpace, "Second policy", "Секретное дополнительное условие.");
        when(index.search(any(), anyString(), anyList(), anyInt(), anyDouble()))
                .thenReturn(List.of(chunk(published), chunk(second)));
        when(provider.generate(anyString(), anyList())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                    "Generation must not keep an earlier SQL snapshot alive");
            List<RagGenerationClient.Context> contexts = invocation.getArgument(1);
            assertEquals(2, contexts.size());
            assertEquals(1, jdbcTemplate.update("DELETE FROM space_user_permissions WHERE space_id = ? AND user_id = ?",
                    secondSpace.getId(), guest.getId()));
            return new RagGenerationClient.GeneratedAnswer("PRIVATE GENERATED ANSWER", List.of(1), false);
        });
        ask(guestJwt).andExpect(status().isConflict())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("SOURCES_CHANGED"))
                .andExpect(jsonPath("$.answer").doesNotExist())
                .andExpect(jsonPath("$.sources").doesNotExist())
                .andExpect(content().string(not(containsString("PRIVATE"))));
        assertFalse(permissionRepository.hasReadAccess(secondSpace.getId(), guest.getId()));
    }

    @Test
    void committedRoleDemotionDuringGenerationIsRecheckedFromSql() throws Exception {
        User reader = persistUser("rag-reader", "password123", "rag-reader@example.test", GlobalRole.READER);
        String readerJwt = loginAndGetJwt(reader.getLogin(), "password123");
        when(provider.generate(anyString(), anyList())).thenAnswer(invocation -> {
            assertEquals(1, jdbcTemplate.update("UPDATE users SET role = ? WHERE id = ?", GlobalRole.GUEST.getDbValue(), reader.getId()));
            return new RagGenerationClient.GeneratedAnswer("PRIVATE AFTER DEMOTION", List.of(1), false);
        });
        ask(readerJwt).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOURCES_CHANGED"))
                .andExpect(content().string(not(containsString("PRIVATE"))));
        assertEquals(GlobalRole.GUEST, userRepository.findById(reader.getId()).orElseThrow().getRole());
    }

    @Test
    void newPublishedGitVersionDuringGenerationInvalidatesOldAnswer() throws Exception {
        grant(allowedSpace, guest);
        when(provider.generate(anyString(), anyList())).thenAnswer(invocation -> {
            documentService.updateDocument(published.getId(), null, "Новый срок — 90 дней.", null, editor.getId());
            return new RagGenerationClient.GeneratedAnswer("OLD PRIVATE ANSWER", List.of(1), false);
        });
        ask(guestJwt).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOURCES_CHANGED"))
                .andExpect(content().string(not(containsString("PRIVATE"))));
    }

    private ResultActions ask(String jwt) throws Exception {
        return mockMvc.perform(post("/api/ai/ask").cookie(jwtCookie(jwt)).with(csrf())
                .contentType(APPLICATION_JSON).content("{\"question\":\"Сколько хранить документы?\"}"));
    }

    private void grant(Space space, User user) {
        permissionRepository.save(SpacePermission.grant(space.getId(), user.getId(), PermissionType.READ));
    }

    private Document publish(Space space, String title, String text) {
        Document document = documentService.createDocument(title, "Initial draft", space.getId(), null, editor.getId(), null);
        documentService.publishDocument(document.getId(), editor.getId(), false);
        documentService.updateDocument(document.getId(), null, text, null, editor.getId());
        // Reload database precision for the same timestamp the production vector query returns.
        return documentService.getDocumentById(document.getId());
    }

    private RagChunk chunk(Document document) {
        var version = versionRepository.findByDocumentIdOrderByCreatedAtAsc(document.getId()).stream()
                .max(Comparator.comparing(com.knowledgebase.domain.model.DocumentVersion::createdAt)
                        .thenComparing(com.knowledgebase.domain.model.DocumentVersion::id)).orElseThrow();
        return new RagChunk(document.getId(), document.getSpaceId(), version.gitHash(), 0,
                "Хранить документы 30 дней.", 0.95, document.getUpdatedAt());
    }
}
