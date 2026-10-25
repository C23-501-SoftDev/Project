package com.knowledgebase.application.service;

import com.knowledgebase.domain.model.*;
import com.knowledgebase.domain.rag.*;
import com.knowledgebase.domain.repository.*;
import com.knowledgebase.infrastructure.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagAnswerServiceTest {
    private static final long USER_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 0, 0);
    private static final String SHA = "a".repeat(40);
    private static final float[] VECTOR = {1, 0, 0};
    private final RagProperties properties = new RagProperties();
    private final DocumentService documents = mock(DocumentService.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentVersionRepository versions = mock(DocumentVersionRepository.class);
    private final SpaceRepository spaces = mock(SpaceRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SpacePermissionRepository directPermissions = mock(SpacePermissionRepository.class);
    private final SpaceGroupPermissionRepository groupPermissions = mock(SpaceGroupPermissionRepository.class);
    private final RagIndexRepository index = mock(RagIndexRepository.class);
    private final RagEmbeddingClient embeddings = mock(RagEmbeddingClient.class);
    private final RagGenerationClient generation = mock(RagGenerationClient.class);
    private final Map<Long, Document> documentState = new LinkedHashMap<>();
    private final Map<Long, Space> spaceState = new HashMap<>();
    private final Map<Long, List<DocumentVersion>> versionState = new HashMap<>();
    private final Set<Long> grantedSpaces = new HashSet<>();
    private final List<RagChunk> candidates = new ArrayList<>();
    private User user;
    private RagAnswerService service;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setEmbeddingDimensions(3);
        user = User.restore(USER_ID, "reader", "hash", "reader@example.test", GlobalRole.READER,
                false, false, null, NOW, NOW);
        when(users.findById(USER_ID)).thenAnswer(invocation -> user.isActive() ? Optional.of(user) : Optional.empty());
        when(documents.getAllAccessibleDocuments(USER_ID, false, false))
                .thenAnswer(invocation -> List.copyOf(documentState.values()));
        when(documentRepository.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(documentState.get(invocation.getArgument(0))));
        when(spaces.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(spaceState.get(invocation.getArgument(0)))
                .filter(space -> !space.isDeleted()));
        when(versions.findByDocumentIdOrderByCreatedAtAsc(anyLong()))
                .thenAnswer(invocation -> versionState.getOrDefault(invocation.getArgument(0), List.of()));
        when(directPermissions.hasReadAccess(anyLong(), eq(USER_ID)))
                .thenAnswer(invocation -> grantedSpaces.contains(invocation.getArgument(0)));
        when(index.isAvailable()).thenReturn(true);
        when(embeddings.embed(anyList())).thenReturn(List.of(VECTOR));
        when(index.search(any(), anyString(), anyList(), anyInt(), anyDouble(), anyInt())).thenAnswer(invocation -> {
            List<Long> allowed = invocation.getArgument(2);
            return candidates.stream().filter(chunk -> allowed.contains(chunk.documentId())).toList();
        });
        when(generation.generate(anyString(), anyList())).thenReturn(
                new RagGenerationClient.GeneratedAnswer("Срок хранения — 30 дней.", List.of(1), false));
        PermissionService permissions = new PermissionService(directPermissions, groupPermissions, users);
        service = new RagAnswerService(properties, documents, documentRepository, versions, spaces, users,
                permissions, index, embeddings, generation, new RagRequestLimiter(properties));
        addDocument(10, 100, DocumentStatus.PUBLISHED);
        candidates.add(chunk(10));
    }

    @Test
    void retrievesOnlyAuthorizedPublishedDocumentsAndBuildsVersionedServerLinks() {
        addDocument(20, 100, DocumentStatus.DRAFT);
        addDocument(21, 100, DocumentStatus.DELETED);
        addDocument(22, 200, DocumentStatus.PUBLISHED);
        user.updateProfile(null, null, GlobalRole.GUEST, false, null);
        grantedSpaces.add(100L);
        RagAnswer result = ask();
        ArgumentCaptor<List<Long>> allowed = ArgumentCaptor.forClass(List.class);
        verify(index).search(eq(VECTOR), eq(properties.getEmbeddingModel()), allowed.capture(), eq(8), eq(0.3), eq(2));
        assertEquals(List.of(10L), allowed.getValue());
        assertFalse(result.insufficientEvidence());
        assertEquals(1, result.sources().size());
        assertEquals(10L, result.sources().get(0).documentId());
        assertEquals("/documents/10", result.sources().get(0).url());
        assertEquals(SHA, result.sources().get(0).versionSha());
        assertNotNull(UUID.fromString(result.requestId()));
    }

    @Test
    void eightArticlesCanReachGenerationAndKeepAllVerifiedCitations() {
        for (long id = 11; id < 18; id++) {
            addDocument(id, 100, DocumentStatus.PUBLISHED);
            candidates.add(chunk(id));
        }
        when(generation.generate(anyString(), anyList())).thenAnswer(invocation -> {
            List<RagGenerationClient.Context> contexts = invocation.getArgument(1);
            assertEquals(8, contexts.size());
            assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), contexts.stream()
                    .map(RagGenerationClient.Context::sourceId).toList());
            return new RagGenerationClient.GeneratedAnswer("Сводка восьми статей.",
                    List.of(1, 2, 3, 4, 5, 6, 7, 8), false);
        });
        RagAnswer answer = ask();
        assertEquals(List.of(10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L),
                answer.sources().stream().map(RagAnswer.Source::documentId).toList());
        assertTrue(answer.sources().stream().allMatch(source -> SHA.equals(source.versionSha())));
    }

    @Test
    void expandedRetrievalStillRespectsContextBudgetAndContiguousSourceIds() {
        properties.setMaxContextChars(256);
        candidates.clear();
        candidates.add(new RagChunk(10, 100, SHA, 0, "x".repeat(230), .99, NOW));
        addDocument(11, 100, DocumentStatus.PUBLISHED);
        candidates.add(new RagChunk(11, 100, SHA, 0, "y".repeat(100), .95, NOW));
        addDocument(12, 100, DocumentStatus.PUBLISHED);
        candidates.add(new RagChunk(12, 100, SHA, 0, "z", .9, NOW));
        when(generation.generate(anyString(), anyList())).thenAnswer(invocation -> {
            List<RagGenerationClient.Context> contexts = invocation.getArgument(1);
            assertEquals(2, contexts.size());
            assertEquals(1, contexts.get(0).sourceId());
            assertEquals(2, contexts.get(1).sourceId());
            assertEquals("Статья 12", contexts.get(1).title());
            assertTrue(contexts.stream().mapToInt(context -> context.title().length() + context.text().length()).sum() <= 256);
            return new RagGenerationClient.GeneratedAnswer("Подтверждённый ответ.", List.of(1, 2), false);
        });
        RagAnswer answer = ask();
        assertEquals(List.of(10L, 12L), answer.sources().stream().map(RagAnswer.Source::documentId).toList());
    }

    @Test
    void servingCanRemainDisabledWhileIndexerConfigurationIsEnabled() {
        properties.setServingEnabled(false);
        assertTrue(properties.isConfigured());
        assertFalse(service.isAvailable());
        assertFailure(RagException.Code.DISABLED);
        verifyNoInteractions(embeddings, generation);
    }

    @Test
    void guestWithoutReadAccessMakesNoEmbeddingOrGenerationCall() {
        user.updateProfile(null, null, GlobalRole.GUEST, false, null);
        RagAnswer answer = ask();
        assertTrue(answer.insufficientEvidence());
        assertTrue(answer.sources().isEmpty());
        verifyNoInteractions(embeddings, generation);
        verify(index, never()).search(any(), anyString(), anyList(), anyInt(), anyDouble(), anyInt());
    }

    @Test
    void selectedSpacesRestrictRetrievalEvenWhenUserCanReadMore() {
        addDocument(11, 200, DocumentStatus.PUBLISHED);
        candidates.add(chunk(11));
        RagAnswer answer = service.ask(USER_ID, false, "Срок?", List.of(200L));
        assertEquals(11L, answer.sources().get(0).documentId());
        verify(index).search(eq(VECTOR), anyString(), eq(List.of(11L)), anyInt(), anyDouble(), anyInt());
    }

    @Test
    void missingOrStaleGitVersionBlocksContextBeforeGeneration() {
        versionState.put(10L, List.of(DocumentVersion.create(10L, "b".repeat(40), USER_ID, "updated", NOW.plusSeconds(1))));
        assertFailure(RagException.Code.SOURCES_CHANGED);
        verifyNoInteractions(generation);
    }

    @Test
    void latestVersionTieUsesIdInsteadOfRepositoryIterationOrder() {
        versionState.put(10L, List.of(
                new DocumentVersion(2L, 10L, "b".repeat(40), "documents/10.md", USER_ID, null, NOW),
                new DocumentVersion(1L, 10L, SHA, "documents/10.md", USER_ID, null, NOW)));
        assertFailure(RagException.Code.SOURCES_CHANGED);
        verifyNoInteractions(generation);
    }

    @Test
    void metadataChangeBlocksContextBeforeGeneration() {
        documentState.get(10L).updateMetadata("Changed title", null);
        assertFailure(RagException.Code.SOURCES_CHANGED);
        verifyNoInteractions(generation);
    }

    @Test
    void permissionRevokedDuringQuestionEmbeddingNeverReachesGeneration() {
        user.updateProfile(null, null, GlobalRole.GUEST, false, null);
        grantedSpaces.add(100L);
        when(embeddings.embed(anyList())).thenAnswer(invocation -> {
            grantedSpaces.clear();
            return List.of(VECTOR);
        });
        assertFailure(RagException.Code.SOURCES_CHANGED);
        verifyNoInteractions(generation);
    }

    @Test
    void revocationOfUncitedContextDuringGenerationDiscardsEntireAnswer() {
        addDocument(11, 200, DocumentStatus.PUBLISHED);
        candidates.add(chunk(11));
        user.updateProfile(null, null, GlobalRole.GUEST, false, null);
        grantedSpaces.addAll(Set.of(100L, 200L));
        when(generation.generate(anyString(), anyList())).thenAnswer(invocation -> {
            List<RagGenerationClient.Context> contexts = invocation.getArgument(1);
            assertEquals(2, contexts.size());
            grantedSpaces.remove(200L); // The model only cites context 1, but context 2 influenced its answer.
            return new RagGenerationClient.GeneratedAnswer("PRIVATE GENERATED ANSWER", List.of(1), false);
        });
        RagException error = assertFailure(RagException.Code.SOURCES_CHANGED);
        assertFalse(error.getMessage().contains("PRIVATE"));
    }

    @Test
    void globalRoleDemotionDuringGenerationDiscardsAnswer() {
        when(generation.generate(anyString(), anyList())).thenAnswer(invocation -> {
            user.updateProfile(null, null, GlobalRole.GUEST, false, null);
            return new RagGenerationClient.GeneratedAnswer("PRIVATE", List.of(1), false);
        });
        assertFailure(RagException.Code.SOURCES_CHANGED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"delete-document", "unpublish-document", "delete-space", "delete-user", "edit-content", "rename-document", "move-space"})
    void sourceLifecycleChangesDuringGenerationDiscardAnswer(String change) {
        when(generation.generate(anyString(), anyList())).thenAnswer(invocation -> {
            switch (change) {
                case "delete-document" -> documentState.get(10L).archive("deleted/10.md");
                case "unpublish-document" -> documentState.get(10L).updateMetadata(null, DocumentStatus.DRAFT);
                case "delete-space" -> spaceState.get(100L).softDelete();
                case "delete-user" -> user.softDelete();
                case "edit-content" -> versionState.put(10L, List.of(DocumentVersion.create(10L,
                        "b".repeat(40), USER_ID, "edit", NOW.plusSeconds(1))));
                case "rename-document" -> documentState.get(10L).updateMetadata("Changed title", null);
                case "move-space" -> documentState.get(10L).moveToSpace(200L);
                default -> throw new AssertionError(change);
            }
            return new RagGenerationClient.GeneratedAnswer("PRIVATE", List.of(1), false);
        });
        assertFailure(RagException.Code.SOURCES_CHANGED);
    }

    @Test
    void emptyRetrievalReturnsSafeInsufficientEvidenceWithoutGeneration() {
        candidates.clear();
        RagAnswer answer = ask();
        assertTrue(answer.insufficientEvidence());
        assertTrue(answer.sources().isEmpty());
        verifyNoInteractions(generation);
    }

    @Test
    void oversizedContextIsNotSentToModel() {
        candidates.clear();
        candidates.add(new RagChunk(10, 100, SHA, 0, "x".repeat(properties.getMaxContextChars()), 1, NOW));
        assertTrue(ask().insufficientEvidence());
        verifyNoInteractions(generation);
    }

    @Test
    void providerInsufficientEvidenceIsReplacedWithFixedSafeMessage() {
        when(generation.generate(anyString(), anyList())).thenReturn(
                new RagGenerationClient.GeneratedAnswer("Arbitrary provider output", List.of(), true));
        RagAnswer answer = ask();
        assertTrue(answer.insufficientEvidence());
        assertTrue(answer.sources().isEmpty());
        assertFalse(answer.answer().contains("Arbitrary"));
    }

    @Test
    void permissionChangeStillDiscardsProviderInsufficientEvidence() {
        when(generation.generate(anyString(), anyList())).thenAnswer(invocation -> {
            user.softDelete();
            return new RagGenerationClient.GeneratedAnswer("Insufficient", List.of(), true);
        });
        assertFailure(RagException.Code.SOURCES_CHANGED);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 2, Integer.MAX_VALUE})
    void invalidProviderCitationIdIsRejected(int sourceId) {
        when(generation.generate(anyString(), anyList())).thenReturn(
                new RagGenerationClient.GeneratedAnswer("PRIVATE", List.of(sourceId), false));
        assertFailure(RagException.Code.PROVIDER_UNAVAILABLE);
    }

    @Test
    void duplicateProviderCitationsAreRejected() {
        addDocument(11, 200, DocumentStatus.PUBLISHED);
        candidates.add(chunk(11));
        when(generation.generate(anyString(), anyList())).thenReturn(
                new RagGenerationClient.GeneratedAnswer("PRIVATE", List.of(1, 1), false));
        assertFailure(RagException.Code.PROVIDER_UNAVAILABLE);
    }

    @Test
    void nonemptyAnswerWithoutCitationsIsRejected() {
        when(generation.generate(anyString(), anyList())).thenReturn(
                new RagGenerationClient.GeneratedAnswer("PRIVATE", List.of(), false));
        assertFailure(RagException.Code.PROVIDER_UNAVAILABLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    void blankQuestionsAreRejectedBeforeProviderCalls(String question) {
        assertEquals(RagException.Code.INVALID_REQUEST, assertThrows(RagException.class,
                () -> service.ask(USER_ID, false, question, List.of())).getCode());
        verifyNoInteractions(embeddings, generation);
    }

    @Test
    void invalidQuestionAndSpaceInputsAreRejected() {
        assertEquals(RagException.Code.INVALID_REQUEST, assertThrows(RagException.class,
                () -> service.ask(USER_ID, false, "x".repeat(2001), List.of())).getCode());
        assertEquals(RagException.Code.INVALID_REQUEST, assertThrows(RagException.class,
                () -> service.ask(null, false, "Question", List.of())).getCode());
        for (List<Long> invalid : List.of(List.of(0L), List.of(-1L), Collections.nCopies(21, 100L))) {
            assertEquals(RagException.Code.INVALID_REQUEST, assertThrows(RagException.class,
                    () -> service.ask(USER_ID, false, "Question", invalid)).getCode());
        }
        verifyNoInteractions(embeddings, generation);
    }

    @Test
    void disabledOrUnavailableIndexNeverCallsProvider() {
        properties.setEnabled(false);
        assertFailure(RagException.Code.DISABLED);
        assertFalse(service.isAvailable());
        properties.setEnabled(true);
        when(index.isAvailable()).thenReturn(false);
        assertFailure(RagException.Code.INDEX_UNAVAILABLE);
        assertFalse(service.isAvailable());
        verifyNoInteractions(embeddings, generation);
    }

    @Test
    void providerFailureIsControlledAndReleasesConcurrencyPermit() {
        when(generation.generate(anyString(), anyList())).thenThrow(
                new RagProviderException(RagProviderException.Code.INVALID_RESPONSE));
        for (int i = 0; i < 3; i++) assertFailure(RagException.Code.PROVIDER_UNAVAILABLE);
        doReturn(new RagGenerationClient.GeneratedAnswer("Recovered", List.of(1), false))
                .when(generation).generate(anyString(), anyList());
        assertEquals("Recovered", ask().answer());
    }

    private RagAnswer ask() { return service.ask(USER_ID, false, "Сколько хранить документы?", List.of()); }
    private RagException assertFailure(RagException.Code code) {
        RagException error = assertThrows(RagException.class, this::ask);
        assertEquals(code, error.getCode());
        return error;
    }
    private Document addDocument(long id, long spaceId, DocumentStatus status) {
        Document document = Document.restore(id, "Статья " + id, "documents/" + id + ".md", status,
                null, USER_ID, spaceId, null, null, null, NOW, NOW);
        documentState.put(id, document);
        spaceState.putIfAbsent(spaceId, Space.restore(spaceId, "Space " + spaceId, "", USER_ID, false, NOW, NOW));
        versionState.put(id, List.of(DocumentVersion.create(id, SHA, USER_ID, "publish", NOW)));
        return document;
    }
    private RagChunk chunk(long id) {
        Document document = documentState.get(id);
        return new RagChunk(id, document.getSpaceId(), SHA, 0, "Срок хранения документов — 30 дней.", 0.9, document.getUpdatedAt());
    }
}
