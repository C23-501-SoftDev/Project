package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.Template;
import java.util.List;
import java.util.Optional;

public interface TemplateRepository {
    List<Template> findAll();
    Optional<Template> findById(Long id);
}
