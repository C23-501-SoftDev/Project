package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.model.Attachment;
import com.knowledgebase.domain.repository.AttachmentRepository;
import com.knowledgebase.infrastructure.persistence.entity.AttachmentJpaEntity;
import com.knowledgebase.infrastructure.persistence.mapper.AttachmentJpaMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Реализация доменного репозитория AttachmentRepository через Spring Data JPA.
 */
@Repository
public class AttachmentRepositoryImpl implements AttachmentRepository {

    private final AttachmentJpaRepository jpaRepository;
    private final AttachmentJpaMapper mapper;

    public AttachmentRepositoryImpl(AttachmentJpaRepository jpaRepository, AttachmentJpaMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Attachment save(Attachment attachment) {
        AttachmentJpaEntity entity = mapper.toEntity(attachment);
        AttachmentJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Attachment> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Attachment> findByDocumentId(Long documentId, boolean includeDeleted) {
        // Attachments do not support soft-delete in the current schema — return all
        List<AttachmentJpaEntity> entities = jpaRepository.findByDocumentIdOrderByUploadedAtDesc(documentId);

        return entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }
}
