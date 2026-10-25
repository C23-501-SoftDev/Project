package com.knowledgebase.integration;

import com.knowledgebase.domain.model.*;
import com.knowledgebase.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses real cookie/header tokens, deliberately without the MockMvc csrf() postprocessor. */
// csrf() replaces the shared filter's repository with a test session repository. Restore production wiring.
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfIntegrationTest extends IntegrationTestBase {
    private Cookie bootstrap() throws Exception {
        Cookie cookie = mockMvc.perform(get("/login")).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        return cookie;
    }

    @Override
    protected String loginAndGetJwt(String login, String password) throws Exception {
        Cookie csrf = bootstrap();
        String body = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(java.util.Map.of("login", login, "password", password));
        var response = mockMvc.perform(post("/api/auth/login").cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString())
                .get("token").asText();
    }

    @Test
    void publicationRejectsMissingAndInvalidTokensWithoutChangingData() throws Exception {
        var author = persistUser("author", "password123", "author@kb.local", GlobalRole.EDITOR, false);
        var space = spaceRepository.save(Space.create("CSRF space", "", author.getId()));
        var document = documentService.createDocument("Protected draft", "", space.getId(), null, author.getId(), null);
        Cookie jwt = jwtCookie(loginAndGetJwt("author", "password123"));
        Cookie csrf = bootstrap();
        String endpoint = "/api/documents/" + document.getId() + "/publish";
        mockMvc.perform(post(endpoint).cookie(jwt)).andExpect(status().isForbidden());
        mockMvc.perform(post(endpoint).cookie(jwt, csrf).header("X-XSRF-TOKEN", "incorrect"))
                .andExpect(status().isForbidden());
        assertEquals(DocumentStatus.DRAFT, documentService.getDocumentById(document.getId()).getStatus());
        mockMvc.perform(post(endpoint).cookie(jwt, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getHeaders("Set-Cookie").stream()
                        .noneMatch(h -> h.startsWith("XSRF-TOKEN=") && h.contains("Max-Age=0")),
                        "JWT authentication must not clear CSRF after each request"));
        assertEquals(DocumentStatus.PUBLISHED, documentService.getDocumentById(document.getId()).getStatus());
        mockMvc.perform(get("/api/documents/" + document.getId()).cookie(jwt, csrf))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getHeaders("Set-Cookie").stream()
                        .noneMatch(h -> h.startsWith("XSRF-TOKEN=") && h.contains("Max-Age=0"))));
        mockMvc.perform(put("/api/documents/" + document.getId()).cookie(jwt, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType(APPLICATION_JSON)
                        .content("{\"content\":\"Second authenticated mutation\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void loginRequiresARealMatchingCsrfCookieAndHeader() throws Exception {
        persistUser("author", "password123", "author@kb.local", GlobalRole.EDITOR, false);
        String body = "{\"login\":\"author\",\"password\":\"password123\"}";
        var denied = mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andReturn();
        assertTrue(denied.getResponse().getHeaders("Set-Cookie").stream().noneMatch(h -> h.startsWith("JWT=")));
        Cookie csrf = bootstrap();
        mockMvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.login").value("author"));
    }

    @Test
    void logoutRequiresTokenAndAcceptsTheHtmlFormParameter() throws Exception {
        persistUser("author", "password123", "author@kb.local", GlobalRole.EDITOR, false);
        Cookie jwt = jwtCookie(loginAndGetJwt("author", "password123"));
        mockMvc.perform(post("/logout").cookie(jwt)).andExpect(status().isForbidden());
        Cookie csrf = bootstrap();
        mockMvc.perform(post("/logout").cookie(jwt, csrf).param("_csrf", csrf.getValue()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }
}
