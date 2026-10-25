package com.knowledgebase.integration;

import com.knowledgebase.domain.model.*;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
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
