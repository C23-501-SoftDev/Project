package com.knowledgebase.integration;

import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.support.IntegrationTestBase;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ErrorPageIntegrationTest extends IntegrationTestBase {

    @Test
    void anonymousProtectedPageRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/some-non-existent-page")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void servlet404DispatchRendersErrorPage() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/missing")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(model().attribute("status", 404))
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
                .andExpect(status().isForbidden());

        // MockMvc does not perform the servlet container's sendError dispatch.
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/admin/users")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(model().attribute("status", 403))
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
