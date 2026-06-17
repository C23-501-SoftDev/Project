package com.knowledgebase.integration;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.infrastructure.persistence.repository.UserGroupJpaRepository;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты управления группами пользователей (US4.1.8).
 *
 * Проверяет сценарии приёмки:
 * - Сценарий 1: Создание группы с уникальным названием → группа доступна в системе.
 * - Сценарий 2: Удаление группы → группа исчезает из системы.
 * А также: валидацию уникальности, обновление, обработку 404 и защиту доступа (только ADMIN).
 */
class UserGroupManagementIntegrationTest extends IntegrationTestBase {

    @Autowired
    private UserGroupJpaRepository userGroupJpaRepository;

    @BeforeEach
    void cleanGroups() {
        // Базовый класс не управляет таблицей групп — чистим её здесь для изоляции тестов.
        userGroupJpaRepository.deleteAll();
    }

    @Test
    void adminGroups_createAndList() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        String name = "group-" + uniqueLogin("g");

        // Сценарий 1: создание группы (201)
        mockMvc.perform(post("/api/admin/groups")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Команда разработки"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is(name)))
                .andExpect(jsonPath("$.description", is("Команда разработки")));

        // группа доступна в списке
        mockMvc.perform(get("/api/admin/groups?page=0&size=50")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].name", is(name)));
    }

    @Test
    void adminGroups_createDuplicateName_returnsConflict() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        String name = "group-" + uniqueLogin("g");
        String body = """
                {"name":"%s","description":"desc"}
                """.formatted(name);

        mockMvc.perform(post("/api/admin/groups")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // повторное создание с тем же именем → 409
        mockMvc.perform(post("/api/admin/groups")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")));
    }

    @Test
    void adminGroups_validationError_returnsBadRequest() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        // пустое имя → 400
        mockMvc.perform(post("/api/admin/groups")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"","description":"desc"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void adminGroups_getById_andNotFound() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        String name = "group-" + uniqueLogin("g");
        String created = mockMvc.perform(post("/api/admin/groups")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = com.jayway.jsonpath.JsonPath.parse(created).read("$.id", Integer.class).longValue();

        // существующая (200)
        mockMvc.perform(get("/api/admin/groups/" + id)
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) id)))
                .andExpect(jsonPath("$.name", is(name)));

        // несуществующая (404)
        mockMvc.perform(get("/api/admin/groups/999999")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void adminGroups_update_andConflictAndNotFound() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        String nameA = "group-a-" + uniqueLogin("g");
        String nameB = "group-b-" + uniqueLogin("g");

        String createdA = mockMvc.perform(post("/api/admin/groups")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"A"}
                                """.formatted(nameA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long idA = com.jayway.jsonpath.JsonPath.parse(createdA).read("$.id", Integer.class).longValue();

        mockMvc.perform(post("/api/admin/groups")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"B"}
                                """.formatted(nameB)))
                .andExpect(status().isCreated());

        // обновление имени и описания группы A (200)
        String newName = "group-a-renamed-" + uniqueLogin("g");
        mockMvc.perform(put("/api/admin/groups/" + idA)
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"A renamed"}
                                """.formatted(newName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(newName)))
                .andExpect(jsonPath("$.description", is("A renamed")));

        // переименование A в имя B → 409
        mockMvc.perform(put("/api/admin/groups/" + idA)
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"conflict"}
                                """.formatted(nameB)))
                .andExpect(status().isConflict());

        // обновление несуществующей → 404
        mockMvc.perform(put("/api/admin/groups/999999")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"whatever","description":"x"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminGroups_delete_andNotFound() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        String name = "group-" + uniqueLogin("g");
        String created = mockMvc.perform(post("/api/admin/groups")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = com.jayway.jsonpath.JsonPath.parse(created).read("$.id", Integer.class).longValue();

        // Сценарий 2: удаление группы (204)
        mockMvc.perform(delete("/api/admin/groups/" + id)
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNoContent());

        // группа исчезла из списка
        mockMvc.perform(get("/api/admin/groups?page=0&size=50")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        // повторное удаление → 404
        mockMvc.perform(delete("/api/admin/groups/" + id)
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminGroups_nonAdmin_isForbidden() throws Exception {
        persistUser("reader", "reader123", "reader@knowledgebase.local", GlobalRole.READER, false);
        String readerJwt = loginAndGetJwt("reader", "reader123");

        mockMvc.perform(get("/api/admin/groups")
                        .cookie(jwtCookie(readerJwt)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/groups")
                        .cookie(jwtCookie(readerJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"hack","description":"x"}
                                """))
                .andExpect(status().isForbidden());
    }
}
