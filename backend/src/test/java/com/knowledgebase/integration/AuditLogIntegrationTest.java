package com.knowledgebase.integration;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционные тесты журнала аудита (US4.1.5 — Логирование действий системы).
 *
 * Покрывают критерии приёмки:
 * - Сценарий 1: операции над документами создают записи в журнале;
 * - Сценарий 2: административные операции (права доступа) журналируются с деталями;
 * - Сценарий 3: журнал доступен только администраторам (403 для остальных);
 * - Сценарий 4: фильтрация по user_id, типу действия и дате.
 */
class AuditLogIntegrationTest extends IntegrationTestBase {

    private long createSpace(String adminJwt, String spaceName) throws Exception {
        String resp = mockMvc.perform(post("/api/admin/spaces")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc"}
                                """.formatted(spaceName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(resp.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));
    }

    @Test
    void documentActions_createAuditEntries() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("audit"));

        // Создание документа
        String docResp = mockMvc.perform(post("/api/documents")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"audit-doc","content":"# Test","spaceId":%d}
                                """.formatted(spaceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long docId = Long.parseLong(docResp.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));

        // Изменение документа
        mockMvc.perform(put("/api/documents/" + docId)
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"content":"# Updated"}
                                """))
                .andExpect(status().isOk());

        // Удаление документа
        mockMvc.perform(delete("/api/documents/" + docId)
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNoContent());

        // Все три действия зафиксированы в журнале с автором и временем
        mockMvc.perform(get("/api/admin/audit?actionType=DOCUMENT_CREATED")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userLogin", is("admin")))
                .andExpect(jsonPath("$.content[0].resourceType", is("DOCUMENT")))
                .andExpect(jsonPath("$.content[0].resourceId", is((int) docId)))
                .andExpect(jsonPath("$.content[0].createdAt", notNullValue()))
                .andExpect(jsonPath("$.content[0].ipAddress", notNullValue()));

        mockMvc.perform(get("/api/admin/audit?actionType=DOCUMENT_UPDATED")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        mockMvc.perform(get("/api/admin/audit?actionType=DOCUMENT_DELETED")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void adminPermissionActions_areAuditedWithDetails() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        User guest = persistUser("guest", "guest123", "guest@knowledgebase.local", GlobalRole.GUEST);
        String adminJwt = loginAndGetJwt("admin", "admin123");
        long spaceId = createSpace(adminJwt, "space-" + uniqueLogin("audit"));

        // Назначение права пользователю
        mockMvc.perform(post("/api/admin/spaces/" + spaceId + "/permissions")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"permissionType":"WRITE"}
                                """.formatted(guest.getId())))
                .andExpect(status().isCreated());

        // Запись в журнале содержит детали: пространство, пользователь, тип права
        mockMvc.perform(get("/api/admin/audit?actionType=PERMISSION_GRANTED")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userLogin", is("admin")))
                .andExpect(jsonPath("$.content[0].resourceType", is("PERMISSION")))
                .andExpect(jsonPath("$.content[0].details", containsString("WRITE")))
                .andExpect(jsonPath("$.content[0].details", containsString("userId=" + guest.getId())));

        // Смена роли пользователя журналируется со старым и новым значением
        mockMvc.perform(put("/api/admin/users/" + guest.getId())
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"login":"%s","email":"%s","role":"READER"}
                                """.formatted(guest.getLogin(), guest.getEmail())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/audit?actionType=USER_UPDATED")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].details", containsString("GUEST")))
                .andExpect(jsonPath("$.content[0].details", containsString("READER")));
    }

    @Test
    void auditLog_isForbiddenForNonAdmins() throws Exception {
        persistUser("reader", "reader123", "reader@knowledgebase.local", GlobalRole.READER);
        persistUser("editor", "editor123", "editor@knowledgebase.local", GlobalRole.EDITOR);

        String readerJwt = loginAndGetJwt("reader", "reader123");
        String editorJwt = loginAndGetJwt("editor", "editor123");

        mockMvc.perform(get("/api/admin/audit").cookie(jwtCookie(readerJwt)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/audit").cookie(jwtCookie(editorJwt)))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditLog_supportsFilteringByUserAndDate() throws Exception {
        User admin1 = persistUser("admin1", "admin123", "admin1@knowledgebase.local", GlobalRole.EDITOR, true);
        User admin2 = persistUser("admin2", "admin123", "admin2@knowledgebase.local", GlobalRole.EDITOR, true);
        String admin1Jwt = loginAndGetJwt("admin1", "admin123");
        String admin2Jwt = loginAndGetJwt("admin2", "admin123");

        // Каждый администратор создаёт своё пространство
        createSpace(admin1Jwt, "space-" + uniqueLogin("a1"));
        createSpace(admin2Jwt, "space-" + uniqueLogin("a2"));

        // Фильтр по user_id: только события admin1
        mockMvc.perform(get("/api/admin/audit?actionType=SPACE_CREATED&userId=" + admin1.getId())
                        .cookie(jwtCookie(admin1Jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userId", is(admin1.getId().intValue())))
                .andExpect(jsonPath("$.content[0].userLogin", is("admin1")));

        // Без фильтра по пользователю — события обоих
        mockMvc.perform(get("/api/admin/audit?actionType=SPACE_CREATED")
                        .cookie(jwtCookie(admin1Jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(2)));

        // Фильтр по дате: будущий dateFrom — пусто
        mockMvc.perform(get("/api/admin/audit?dateFrom=2100-01-01T00:00:00")
                        .cookie(jwtCookie(admin1Jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is(empty())));

        // Фильтр по дате: прошедший dateFrom — записи есть
        mockMvc.perform(get("/api/admin/audit?dateFrom=2000-01-01T00:00:00&actionType=SPACE_CREATED")
                        .cookie(jwtCookie(admin1Jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        // Фильтр по userId, которого нет в событиях admin2
        mockMvc.perform(get("/api/admin/audit?actionType=SPACE_CREATED&userId=" + admin2.getId())
                        .cookie(jwtCookie(admin2Jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userLogin", is("admin2")));
    }
}
