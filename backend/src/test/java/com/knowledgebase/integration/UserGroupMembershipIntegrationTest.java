package com.knowledgebase.integration;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.infrastructure.persistence.repository.UserGroupJpaRepository;
import com.knowledgebase.infrastructure.persistence.repository.UserGroupMemberJpaRepository;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты управления членством в группах (US4.1.9).
 *
 * Проверяет сценарии приёмки:
 * - Сценарий 1: Добавление пользователя в группу → пользователь становится членом группы.
 * - Сценарий 2: Удаление пользователя из группы → пользователь перестаёт быть членом.
 * А также: 404 (нет группы/пользователя), 409 (повторное добавление), 404 (удаление не-члена),
 * каскадное удаление состава при удалении группы и защиту доступа (только ADMIN).
 */
class UserGroupMembershipIntegrationTest extends IntegrationTestBase {

    @Autowired
    private UserGroupJpaRepository userGroupJpaRepository;

    @Autowired
    private UserGroupMemberJpaRepository userGroupMemberJpaRepository;

    @BeforeEach
    void cleanGroups() {
        // Базовый класс не управляет таблицами групп — чистим их здесь (сначала состав, потом группы).
        userGroupMemberJpaRepository.deleteAll();
        userGroupJpaRepository.deleteAll();
    }

    private long createGroup(String adminJwt, String name) throws Exception {
        String created = mockMvc.perform(post("/api/admin/groups")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.parse(created).read("$.id", Integer.class).longValue();
    }

    @Test
    void addAndListAndRemoveMember() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        User member = persistUser("member1", "member123", "member1@knowledgebase.local", GlobalRole.READER, false);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        long groupId = createGroup(adminJwt, "group-" + uniqueLogin("g"));

        // Сценарий 1: добавление пользователя в группу (201)
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/members")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + member.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.membershipId", notNullValue()))
                .andExpect(jsonPath("$.groupId", is((int) groupId)))
                .andExpect(jsonPath("$.userId", is(member.getId().intValue())))
                .andExpect(jsonPath("$.login", is("member1")))
                .andExpect(jsonPath("$.role", is("READER")));

        // пользователь виден в составе группы
        mockMvc.perform(get("/api/admin/groups/" + groupId + "/members")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId", is(member.getId().intValue())));

        // Сценарий 2: удаление пользователя из группы (204)
        mockMvc.perform(delete("/api/admin/groups/" + groupId + "/members/" + member.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNoContent());

        // состав группы пуст
        mockMvc.perform(get("/api/admin/groups/" + groupId + "/members")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void addMember_duplicate_returnsConflict() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        User member = persistUser("member1", "member123", "member1@knowledgebase.local", GlobalRole.READER, false);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        long groupId = createGroup(adminJwt, "group-" + uniqueLogin("g"));
        String body = "{\"userId\":" + member.getId() + "}";

        mockMvc.perform(post("/api/admin/groups/" + groupId + "/members")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // повторное добавление → 409
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/members")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    void addMember_unknownGroupOrUser_returnsNotFound() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        User member = persistUser("member1", "member123", "member1@knowledgebase.local", GlobalRole.READER, false);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        // несуществующая группа → 404
        mockMvc.perform(post("/api/admin/groups/999999/members")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + member.getId() + "}"))
                .andExpect(status().isNotFound());

        long groupId = createGroup(adminJwt, "group-" + uniqueLogin("g"));

        // несуществующий пользователь → 404
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/members")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":888888}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeMember_notAMember_returnsNotFound() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        User member = persistUser("member1", "member123", "member1@knowledgebase.local", GlobalRole.READER, false);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        long groupId = createGroup(adminJwt, "group-" + uniqueLogin("g"));

        // пользователь не состоит в группе → 404
        mockMvc.perform(delete("/api/admin/groups/" + groupId + "/members/" + member.getId())
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listMembers_unknownGroup_returnsNotFound() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        mockMvc.perform(get("/api/admin/groups/999999/members")
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteGroup_removesMemberships() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.EDITOR, true);
        User member = persistUser("member1", "member123", "member1@knowledgebase.local", GlobalRole.READER, false);
        String adminJwt = loginAndGetJwt("admin", "admin123");

        long groupId = createGroup(adminJwt, "group-" + uniqueLogin("g"));

        mockMvc.perform(post("/api/admin/groups/" + groupId + "/members")
                        .cookie(jwtCookie(adminJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + member.getId() + "}"))
                .andExpect(status().isCreated());

        // удаляем группу
        mockMvc.perform(delete("/api/admin/groups/" + groupId)
                        .cookie(jwtCookie(adminJwt)))
                .andExpect(status().isNoContent());

        // состав группы должен быть удалён, пользователь — остаться
        org.junit.jupiter.api.Assertions.assertEquals(0, userGroupMemberJpaRepository.countByGroupId(groupId));
        org.junit.jupiter.api.Assertions.assertTrue(userRepository.findById(member.getId()).isPresent());
    }

    @Test
    void membership_nonAdmin_isForbidden() throws Exception {
        persistUser("reader", "reader123", "reader@knowledgebase.local", GlobalRole.READER, false);
        String readerJwt = loginAndGetJwt("reader", "reader123");

        mockMvc.perform(get("/api/admin/groups/1/members")
                        .cookie(jwtCookie(readerJwt)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/groups/1/members")
                        .cookie(jwtCookie(readerJwt))
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":1}"))
                .andExpect(status().isForbidden());
    }
}
