package com.knowledgebase.integration;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIntegrationTest extends IntegrationTestBase {

    @Test
    void publicEndpoints_areAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void adminApi_requiresAuthentication_andAdminRole() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.ADMIN);
        persistUser("reader", "reader123", "reader@knowledgebase.local", GlobalRole.READER);

        // Не аутентифицирован
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());

        // Аутентифицирован, но не ADMIN
        String readerJwt = loginAndGetJwt("reader", "reader123");
        mockMvc.perform(get("/api/admin/users").cookie(jwtCookie(readerJwt)))
                .andExpect(status().isForbidden());

        // ADMIN
        String adminJwt = loginAndGetJwt("admin", "admin123");
        mockMvc.perform(get("/api/admin/users").cookie(jwtCookie(adminJwt)))
                .andExpect(status().isOk());
    }
}

