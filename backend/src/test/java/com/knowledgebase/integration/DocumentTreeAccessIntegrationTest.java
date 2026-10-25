package com.knowledgebase.integration;

import com.knowledgebase.domain.model.*;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentTreeAccessIntegrationTest extends IntegrationTestBase {
    @Test
    void treeHidesOtherAuthorsDraftsAndEnforcesSpaceAccess() throws Exception {
        var author = persistUser("author", "password123", "author@kb.local", GlobalRole.EDITOR, false);
        persistUser("admin", "password123", "admin@kb.local", GlobalRole.EDITOR, true);
        persistUser("reader", "password123", "reader@kb.local", GlobalRole.READER, false);
        persistUser("editor", "password123", "editor@kb.local", GlobalRole.EDITOR, false);
        persistUser("guest", "password123", "guest@kb.local", GlobalRole.GUEST, false);
        var space = spaceRepository.save(Space.create("Tree visibility", "", author.getId()));
        documentService.createDocument("Private tree draft", "", space.getId(), null, author.getId(), null);
        var published = documentService.createDocument("Visible tree document", "", space.getId(), null, author.getId(), null);
        documentService.publishDocument(published.getId(), author.getId(), false);
        var deleted = documentService.createDocument("Deleted tree document", "", space.getId(), null, author.getId(), null);
        documentService.deleteDocument(deleted.getId());
        String endpoint = "/spaces/" + space.getId() + "/tree";
        for (String login : new String[]{"reader", "editor"}) {
            mockMvc.perform(get(endpoint).cookie(jwtCookie(loginAndGetJwt(login, "password123"))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("text/html;charset=UTF-8"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().string(containsString("Visible tree document")))
                    .andExpect(content().string(not(containsString("Private tree draft"))))
                    .andExpect(content().string(not(containsString("Deleted tree document"))));
        }
        for (String login : new String[]{"author", "admin"}) {
            mockMvc.perform(get(endpoint).cookie(jwtCookie(loginAndGetJwt(login, "password123"))))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Private tree draft")));
        }
        mockMvc.perform(get(endpoint).cookie(jwtCookie(loginAndGetJwt("guest", "password123"))))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("Visible tree document"))));
    }

    @Test
    void htmlFragmentPreservesSafeTitlesHierarchyAndActiveLinkWithoutTemplateRendering() throws Exception {
        var author = persistUser("author", "password123", "author@kb.local", GlobalRole.EDITOR, false);
        var space = spaceRepository.save(Space.create("Rendered tree", "", author.getId()));
        String title = "Книга \"цитаты\" & <img src=x onerror=alert(1)> ?#";
        var parent = documentService.createDocument(title, "", space.getId(), null, author.getId(), null);
        var child = documentService.createDocument("Дочерняя запись", "", space.getId(), parent.getId(), author.getId(), null);
        var sibling = documentService.createDocument("Последняя запись", "", space.getId(), null, author.getId(), null);
        var cookie = jwtCookie(loginAndGetJwt("author", "password123"));
        var response = mockMvc.perform(get("/spaces/" + space.getId() + "/tree")
                        .param("documentId", child.getId().toString()).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andReturn().getResponse();
        var html = Jsoup.parseBodyFragment(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(List.of(parent.getId().toString(), child.getId().toString(), sibling.getId().toString()),
                html.select(".document-item").eachAttr("data-document-id"));
        assertTrue(html.select("img, script, [onerror]").isEmpty());
        var parentLink = html.selectFirst("[data-document-id=" + parent.getId() + "] > .document-row > a");
        assertEquals(title, parentLink.text());
        assertEquals("/spaces/" + space.getId() + "/doc/" + title, URI.create(parentLink.attr("href")).getPath());
        assertEquals("Дочерняя запись", html.select("a.active").text());
        assertEquals(1, html.select("a.active").size());
        assertEquals(1, html.select("ul.nested-document-list > [data-document-id=" + child.getId() + "]").size());
        assertEquals(2, html.select(".document-tree-end-drop-zone").size());

        // Reusing the renderer must not retain this request's active selection or user HTML.
        String second = mockMvc.perform(get("/spaces/" + space.getId() + "/tree").cookie(cookie))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(Jsoup.parseBodyFragment(second).select("a.active").isEmpty());
    }

    @Test
    void initialPageDoesNotEmbedTreeRows() throws Exception {
        var author = persistUser("author", "password123", "author@kb.local", GlobalRole.EDITOR, false);
        var space = spaceRepository.save(Space.create("Lazy tree", "", author.getId()));
        documentService.createDocument("Draft absent from initial HTML", "", space.getId(), null, author.getId(), null);
        mockMvc.perform(get("/").cookie(jwtCookie(loginAndGetJwt("author", "password123"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/spaces/" + space.getId() + "/tree")))
                .andExpect(content().string(not(containsString("Draft absent from initial HTML"))))
                .andExpect(content().string(not(containsString("class=\"document-row\""))));
    }
}
