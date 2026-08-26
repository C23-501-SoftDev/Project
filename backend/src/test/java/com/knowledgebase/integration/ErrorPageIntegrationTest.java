package com.knowledgebase.integration;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ErrorPageIntegrationTest extends IntegrationTestBase {

    @Test
    void whenAccessingNonExistentPage_returnsBeautiful404Html() throws Exception {
        mockMvc.perform(get("/some-non-existent-page")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/error"))
                .andExpect(content().string(containsString("404")))
                .andExpect(content().string(containsString("Страница не найдена")));
    }

    @Test
    void whenAccessingForbiddenPage_returnsBeautiful403Html() throws Exception {
        persistUser("reader", "reader123", "reader@knowledgebase.local", GlobalRole.READER);
        String readerJwt = loginAndGetJwt("reader", "reader123");

        // /admin/users requires ADMIN role
        mockMvc.perform(get("/admin/users")
                        .cookie(jwtCookie(readerJwt))
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/error"))
                .andExpect(content().string(containsString("403")))
                .andExpect(content().string(containsString("Доступ запрещён")));
    }

    @Test
    void whenApiEndpointAccessDenied_returnsJsonNotHtml() throws Exception {
        persistUser("reader", "reader123", "reader@knowledgebase.local", GlobalRole.READER);
        String readerJwt = loginAndGetJwt("reader", "reader123");

        // /api/admin/users requires ADMIN role
        mockMvc.perform(get("/api/admin/users")
                        .cookie(jwtCookie(readerJwt))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value(containsString("Недостаточно прав")));
    }
}
