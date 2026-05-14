package com.knowledgebase.infrastructure.persistence.repository;

import com.knowledgebase.domain.model.User;
import com.knowledgebase.domain.repository.UserRepository;
import com.knowledgebase.infrastructure.persistence.entity.UserJpaEntity;
import com.knowledgebase.infrastructure.persistence.mapper.UserJpaMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Реализация доменного репозитория UserRepository через Spring Data JPA.
 *
 * Это адаптер (Adapter pattern): преобразует доменные объекты в JPA-сущности и обратно.
 *
 * Слои зависимостей:
 *   Domain (UserRepository интерфейс)
 *     ↑ implements
 *   Infrastructure (UserRepositoryImpl) → Spring Data JPA (UserJpaRepository)
 *
 * Для проверок ON DELETE RESTRICT (documents, versions) используем JdbcTemplate:
 * - Позволяет безопасно проверить таблицы, которые ещё не существуют в MVP
 * - При добавлении таблиц в следующих итерациях код просто начнёт работать
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepositoryImpl.class);

    private final UserJpaRepository jpaRepository;
    private final UserJpaMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    public UserRepositoryImpl(UserJpaRepository jpaRepository,
                               UserJpaMapper mapper,
                               JdbcTemplate jdbcTemplate) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = mapper.toJpaEntity(user);
        UserJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpaRepository.findByIdAndIsDeletedFalse(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByIdIncludingDeleted(Long id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByLogin(String login) {
        return jpaRepository.findByLoginAndIsDeletedFalse(login)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByLoginIncludingDeleted(String login) {
        return jpaRepository.findByLogin(login)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmailAndIsDeletedFalse(email)
                .map(mapper::toDomain);
    }

    @Override
    public List<User> findAllActive(int page, int size, String sortBy, String sortDir) {
        String safeSortBy = List.of("id", "login", "email", "role", "createdAt", "updatedAt")
                .contains(sortBy) ? sortBy : "createdAt";

        Sort sort = Sort.Direction.DESC.name().equalsIgnoreCase(sortDir)
                ? Sort.by(safeSortBy).descending()
                : Sort.by(safeSortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return jpaRepository.findByIsDeletedFalse(pageable)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<User> findAllIncludingDeleted(int page, int size, String sortBy, String sortDir) {
        String safeSortBy = List.of("id", "login", "email", "role", "createdAt", "updatedAt")
                .contains(sortBy) ? sortBy : "createdAt";

        Sort sort = Sort.Direction.DESC.name().equalsIgnoreCase(sortDir)
                ? Sort.by(safeSortBy).descending()
                : Sort.by(safeSortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return jpaRepository.findAll(pageable)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long countActive() {
        return jpaRepository.countByIsDeletedFalse();
    }

    @Override
    public long countAll() {
        return jpaRepository.count();
    }

    @Override
    public boolean existsByLogin(String login) {
        return jpaRepository.existsByLoginAndIsDeletedFalse(login);
    }

    @Override
    public boolean existsByLoginIncludingDeleted(String login) {
        return jpaRepository.existsByLogin(login);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmailAndIsDeletedFalse(email);
    }

    @Override
    public boolean existsByEmailIncludingDeleted(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public boolean hasDocuments(Long userId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT CAST(COUNT(*) AS INT) FROM documents WHERE author_id = ?",
                    Integer.class,
                    userId
            );
            return count != null && count > 0;
        } catch (DataAccessException e) {
            log.debug("Таблица documents не найдена при проверке пользователя {}: {}", userId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean hasOwnedSpaces(Long userId) {
        return jpaRepository.hasOwnedSpaces(userId);
    }

    @Override
    public boolean hasVersions(Long userId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT CAST(COUNT(*) AS INT) FROM versions WHERE author_id = ?",
                    Integer.class,
                    userId
            );
            return count != null && count > 0;
        } catch (DataAccessException e) {
            log.debug("Таблица versions не найдена при проверке пользователя {}: {}", userId, e.getMessage());
            return false;
        }
    }
}
