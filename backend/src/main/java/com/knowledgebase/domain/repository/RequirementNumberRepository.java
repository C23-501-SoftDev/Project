package com.knowledgebase.domain.repository;

public interface RequirementNumberRepository {
    int allocateNextRequirementNumber(Long spaceId, Long templateId);
}