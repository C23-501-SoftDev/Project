package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.repository.RequirementNumberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RequirementNumberRepositoryImpl implements RequirementNumberRepository {

    private final JdbcTemplate jdbcTemplate;

    public RequirementNumberRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int allocateNextRequirementNumber(Long spaceId, Long templateId) {
        Integer currentValue = loadCurrentValueForUpdate(spaceId, templateId);
        if (currentValue != null) {
            jdbcTemplate.update(
                    """
                    UPDATE requirement_number_counters
                    SET next_number = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE space_id = ? AND template_id = ?
                    """,
                    currentValue + 1,
                    spaceId,
                    templateId
            );
            return currentValue;
        }

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO requirement_number_counters (space_id, template_id, next_number, created_at, updated_at)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    spaceId,
                    templateId,
                    2
            );
            return 1;
        } catch (DataIntegrityViolationException ex) {
            Integer retryValue = loadCurrentValueForUpdate(spaceId, templateId);
            if (retryValue == null) {
                throw ex;
            }

            jdbcTemplate.update(
                    """
                    UPDATE requirement_number_counters
                    SET next_number = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE space_id = ? AND template_id = ?
                    """,
                    retryValue + 1,
                    spaceId,
                    templateId
            );
            return retryValue;
        }
    }

    private Integer loadCurrentValueForUpdate(Long spaceId, Long templateId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT next_number
                    FROM requirement_number_counters
                    WHERE space_id = ? AND template_id = ?
                    FOR UPDATE
                    """,
                    Integer.class,
                    spaceId,
                    templateId
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }
}