package com.knowledgebase.domain.repository;

import com.knowledgebase.domain.model.Space;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Интерфейс репозитория пространств (Domain Layer).
 *
 * Реализация: {@link com.knowledgebase.infrastructure.persistence.repository.SpaceRepositoryImpl}
 */
public interface SpaceRepository {

    /**
     * Сохраняет пространство.
     * @param space пространство для сохранения
     * @return сохранённое пространство с ID
     */
    Space save(Space space);

    /**
     * Находит пространство по ID.
     */
    Optional<Space> findById(Long id);

    /**
     * Находит пространства по списку ID.
     */
    List<Space> findAllByIdIn(Set<Long> ids);

    /**
     * Находит пространство по уникальному имени.
     */
    Optional<Space> findByName(String name);

    /**
     * Возвращает все пространства с пагинацией (для ADMIN).
     */
    List<Space> findAll(int page, int size);

    /**
     * Возвращает пространства, которыми владеет пользователь.
     * @param ownerId ID пользователя-владельца
     */
    List<Space> findByOwnerId(Long ownerId);

    /**
     * Удаляет пространство по ID.
     */
    void deleteById(Long id);

    /**
     * Проверяет, существует ли пространство с данным именем.
     */
    boolean existsByName(String name);

    /**
     * Проверяет, существует ли пространство с данным именем, исключая указанный ID.
     */
    boolean existsByNameAndIdNot(String name, Long id);

    /**
     * Возвращает общее количество пространств.
     */
    long count();
}
