package com.knowledgebase.application.dto;

import java.util.List;

/**
 * Универсальная обёртка для результатов с пагинацией на application-уровне.
 * Не привязана к REST DTO (интерфейсному слою).
 */
public record PagedResult<T>(List<T> items, long totalElements) {}

