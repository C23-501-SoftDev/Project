package com.knowledgebase.integration;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void login_success_setsJwtCookie_andReturnsUser() throws Exception {
        User user = persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.ADMIN);

        String body = """
                {"login":"%s","password":"%s"}
                """.formatted(user.getLogin(), "admin123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.user.id", is(user.getId().intValue())))
                .andExpect(jsonPath("$.user.login", is("admin")))
                .andExpect(jsonPath("$.user.role", is("ADMIN")))
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        // AuthController ставит cookie через заголовок вручную
        org.junit.jupiter.api.Assertions.assertNotNull(setCookie);
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("JWT="));
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("HttpOnly"));
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("SameSite=Lax"));
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("Path=/"));
    }

    @Test
    void login_invalidCredentials_returns401ErrorResponse() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.ADMIN);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"login\":\"admin\",\"password\":\"wrong12\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.path", is("/api/auth/login")))
                .andExpect(jsonPath("$.message", not(emptyOrNullString())));
    }

    @Test
    void me_withoutJwtCookie_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withJwtCookie_returnsCurrentUser() throws Exception {
        persistUser("admin", "admin123", "admin@knowledgebase.local", GlobalRole.ADMIN);
        String jwt = loginAndGetJwt("admin", "admin123");

        mockMvc.perform(get("/api/auth/me").cookie(jwtCookie(jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login", is("admin")))
                .andExpect(jsonPath("$.role", is("ADMIN")))
                .andExpect(jsonPath("$.email", is("admin@knowledgebase.local")));
    }
}

