package com.knowledgebase;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Базовый тест загрузки контекста приложения.
 *
 * Использует профиль "test" с H2 in-memory БД.
 * Проверяет, что все бины поднимаются без ошибок.
 */
@SpringBootTest
@ActiveProfiles("test")
class KnowledgeBaseApplicationTests {

    @Test
    void contextLoads() {
        // Если контекст поднялся без исключений — тест пройден
    }
}
