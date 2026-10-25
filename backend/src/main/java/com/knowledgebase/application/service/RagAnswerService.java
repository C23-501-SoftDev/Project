package com.knowledgebase.application.service;

import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.DocumentStatus;
import com.knowledgebase.domain.model.DocumentVersion;
import com.knowledgebase.domain.rag.*;
import com.knowledgebase.domain.repository.*;
import com.knowledgebase.infrastructure.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/** Each authorization check opens fresh repository transactions; never hold a read snapshot during generation. */
@Service
public class RagAnswerService {
    private static final Logger log = LoggerFactory.getLogger(RagAnswerService.class);
    private static final String INSUFFICIENT = "В доступных опубликованных статьях недостаточно сведений для ответа.";
    private final RagProperties properties;
    private final DocumentService documents;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versions;
    private final SpaceRepository spaces;
    private final UserRepository users;
    private final PermissionService permissions;
    private final RagIndexRepository index;
    private final RagEmbeddingClient embeddings;
    private final RagGenerationClient generation;
    private final RagRequestLimiter limiter;

    public RagAnswerService(RagProperties properties, DocumentService documents, DocumentRepository documentRepository,
                            DocumentVersionRepository versions, SpaceRepository spaces, UserRepository users,
                            PermissionService permissions, RagIndexRepository index, RagEmbeddingClient embeddings,
                            RagGenerationClient generation, RagRequestLimiter limiter) {
        this.properties=properties; this.documents=documents; this.documentRepository=documentRepository;
        this.versions=versions; this.spaces=spaces; this.users=users; this.permissions=permissions;
        this.index=index; this.embeddings=embeddings; this.generation=generation; this.limiter=limiter;
    }
    public boolean isAvailable() {
        if (!properties.isConfigured() || !properties.isServingEnabled()) return false;
        try { return index.isAvailable(); } catch (RuntimeException ex) { return false; }
    }
    public RagAnswer ask(Long userId, boolean isAdmin, String question, List<Long> spaceIds) {
        if (userId == null || question == null || question.isBlank() || question.strip().length() > 2000
                || (spaceIds != null && (spaceIds.size() > 20 || spaceIds.stream().anyMatch(id -> id == null || id <= 0))))
            throw new RagException(RagException.Code.INVALID_REQUEST);
        if (!properties.isConfigured() || !properties.isServingEnabled()) throw new RagException(RagException.Code.DISABLED);
        if (!isAvailable()) throw new RagException(RagException.Code.INDEX_UNAVAILABLE);
        String requestId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        try (var ignored = limiter.acquire(userId)) {
            if (users.findById(userId).isEmpty()) throw new RagException(RagException.Code.SOURCES_CHANGED);
            Set<Long> requested = spaceIds == null ? Set.of() : new HashSet<>(spaceIds);
            List<Long> allowed = documents.getAllAccessibleDocuments(userId, isAdmin, false).stream()
                    .filter(d -> d.getStatus() == DocumentStatus.PUBLISHED)
                    .filter(d -> requested.isEmpty() || requested.contains(d.getSpaceId()))
                    .filter(d -> spaces.findById(d.getSpaceId()).isPresent())
                    .filter(d -> permissions.canRead(userId, isAdmin, d.getSpaceId()))
                    .map(Document::getId).toList();
            if (allowed.isEmpty()) return insufficient(requestId);
            float[] vector = embeddings.embed(List.of(question.strip())).get(0);
            List<RagChunk> candidates = index.search(vector, properties.getEmbeddingModel(), allowed,
                    properties.getTopK(), properties.getMinimumScore());
            List<RagChunk> selected = new ArrayList<>();
            List<RagGenerationClient.Context> contexts = new ArrayList<>();
            Map<Long, Document> sourceDocuments = new HashMap<>();
            int remaining = properties.getMaxContextChars();
            for (RagChunk chunk : candidates) {
                Document doc = current(userId, isAdmin, chunk);
                int length = doc.getTitle().length() + chunk.text().length();
                if (length > remaining) continue;
                remaining -= length; selected.add(chunk); sourceDocuments.put(doc.getId(), doc);
                contexts.add(new RagGenerationClient.Context(selected.size(), doc.getTitle(), chunk.text()));
            }
            if (selected.isEmpty()) return insufficient(requestId);
            // Recheck every supplied source at the last point before sending any context to Ollama.
            selected.forEach(c -> current(userId, isAdmin, c));
            RagGenerationClient.GeneratedAnswer generated = generation.generate(question.strip(), contexts);
            // Check ALL context, even sources the model did not cite: the entire answer may depend on them.
            selected.forEach(c -> current(userId, isAdmin, c));
            if (generated.insufficientEvidence()) return insufficient(requestId);
            if (generated.answer() == null || generated.answer().isBlank() || generated.sourceIds() == null
                    || generated.sourceIds().isEmpty() || generated.sourceIds().size() > selected.size())
                throw new RagException(RagException.Code.PROVIDER_UNAVAILABLE);
            Set<Integer> seen = new HashSet<>();
            List<RagAnswer.Source> sources = new ArrayList<>();
            for (Integer sourceId : generated.sourceIds()) {
                if (sourceId == null || sourceId < 1 || sourceId > selected.size() || !seen.add(sourceId))
                    throw new RagException(RagException.Code.PROVIDER_UNAVAILABLE);
                RagChunk chunk = selected.get(sourceId - 1); Document doc = sourceDocuments.get(chunk.documentId());
                sources.add(new RagAnswer.Source(doc.getId(), doc.getTitle(), "/documents/" + doc.getId(),
                        chunk.versionSha(), chunk.chunkIndex()));
            }
            log.info("rag request={} model={} sources={} durationMs={}", requestId, properties.getGenerationModel(),
                    sources.stream().map(RagAnswer.Source::documentId).toList(), (System.nanoTime()-started)/1_000_000);
            return new RagAnswer(generated.answer(), sources, false, requestId);
        } catch (RagException ex) { throw ex;
        } catch (RagProviderException ex) {
            log.warn("rag request={} providerFailure durationMs={}", requestId, (System.nanoTime()-started)/1_000_000);
            throw new RagException(RagException.Code.PROVIDER_UNAVAILABLE);
        } catch (RuntimeException ex) {
            log.warn("rag request={} unavailable type={}", requestId, ex.getClass().getSimpleName());
            throw new RagException(RagException.Code.INDEX_UNAVAILABLE);
        }
    }
    private Document current(Long userId, boolean isAdmin, RagChunk chunk) {
        Document doc = documentRepository.findById(chunk.documentId()).orElse(null);
        if (users.findById(userId).isEmpty() || doc == null || doc.getStatus() != DocumentStatus.PUBLISHED
                || !Objects.equals(doc.getSpaceId(), chunk.spaceId()) || spaces.findById(doc.getSpaceId()).isEmpty()
                || !permissions.canRead(userId, isAdmin, doc.getSpaceId())
                || !Objects.equals(doc.getUpdatedAt(), chunk.sourceUpdatedAt()))
            throw new RagException(RagException.Code.SOURCES_CHANGED);
        List<DocumentVersion> currentVersions = versions.findByDocumentIdOrderByCreatedAtAsc(doc.getId());
        DocumentVersion latest = currentVersions.stream().max(Comparator.comparing(DocumentVersion::createdAt)
                .thenComparing(v -> v.id() == null ? 0L : v.id())).orElse(null);
        if (latest == null || !chunk.versionSha().equals(latest.gitHash()))
            throw new RagException(RagException.Code.SOURCES_CHANGED);
        return doc;
    }
    private RagAnswer insufficient(String id) { return new RagAnswer(INSUFFICIENT, List.of(), true, id); }
}
