package com.knowledgebase.infrastructure.persistence.mapper;

import com.knowledgebase.domain.model.Template;
import com.knowledgebase.infrastructure.persistence.entity.TemplateJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class TemplateJpaMapper {
    public Template toDomain(TemplateJpaEntity entity) {
        return new Template(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getContent(),
            entity.getRole(),
            entity.isSystem(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public List<Template> toDomainList(List<TemplateJpaEntity> entities) {
        return entities.stream().map(this::toDomain).collect(Collectors.toList());
    }
}
