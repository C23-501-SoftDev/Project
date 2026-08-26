package com.knowledgebase.integration;

import com.knowledgebase.application.service.DocumentService.DocumentTreeNode;
import com.knowledgebase.domain.model.Document;
import com.knowledgebase.domain.model.GlobalRole;
import com.knowledgebase.domain.model.Space;
import com.knowledgebase.domain.model.User;
import com.knowledgebase.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentOrderingIntegrationTest extends IntegrationTestBase {

    @Test
    void rootDocumentsCanBeReorderedWithinSpace() throws Exception {
        TestContext context = createContext("root-order");
        Document first = createDocument(context, "First", null);
        Document second = createDocument(context, "Second", null);
        Document third = createDocument(context, "Third", null);

        move(context, third.getId(), context.space().getId(), null, 0);

        List<DocumentTreeNode> roots = tree(context.space().getId());
        assertEquals(List.of(third.getId(), first.getId(), second.getId()), ids(roots));
        assertEquals(List.of(0, 1, 2), sortOrders(roots));

        mockMvc.perform(get("/").cookie(jwtCookie(context.jwt())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"document-row\"")))
                .andExpect(content().string(containsString("data-sort-order=\"0\"")));
    }

    @Test
    void childrenCanBeReorderedWithinSameParent() throws Exception {
        TestContext context = createContext("child-order");
        Document parent = createDocument(context, "Parent", null);
        Document first = createDocument(context, "Child First", parent.getId());
        Document second = createDocument(context, "Child Second", parent.getId());
        Document third = createDocument(context, "Child Third", parent.getId());

        move(context, third.getId(), context.space().getId(), parent.getId(), 0);

        DocumentTreeNode parentNode = tree(context.space().getId()).stream()
                .filter(node -> node.getDocument().getId().equals(parent.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(third.getId(), first.getId(), second.getId()), ids(parentNode.getChildren()));
        assertEquals(List.of(0, 1, 2), sortOrders(parentNode.getChildren()));
    }

    @Test
    void documentCanBeInsertedAtExactPositionUnderAnotherParent() throws Exception {
        TestContext context = createContext("parent-order");
        Document sourceParent = createDocument(context, "Source Parent", null);
        Document targetParent = createDocument(context, "Target Parent", null);
        Document moved = createDocument(context, "Moved Child", sourceParent.getId());
        Document targetFirst = createDocument(context, "Target First", targetParent.getId());
        Document targetSecond = createDocument(context, "Target Second", targetParent.getId());

        move(context, moved.getId(), context.space().getId(), targetParent.getId(), 1);

        List<DocumentTreeNode> roots = tree(context.space().getId());
        DocumentTreeNode sourceNode = findNode(roots, sourceParent.getId());
        DocumentTreeNode targetNode = findNode(roots, targetParent.getId());

        assertEquals(List.of(), ids(sourceNode.getChildren()));
        assertEquals(List.of(targetFirst.getId(), moved.getId(), targetSecond.getId()), ids(targetNode.getChildren()));
        assertEquals(List.of(0, 1, 2), sortOrders(targetNode.getChildren()));
        assertEquals(targetParent.getId(), documentService.getDocumentById(moved.getId()).getParentDocumentId());
    }

    @Test
    void subtreeCanBeMovedBetweenSpacesAtExactRootPosition() throws Exception {
        TestContext source = createContext("space-order");
        Space targetSpace = spaceRepository.save(Space.create(
                "target-space-" + System.nanoTime(), "", source.user().getId()));
        TestContext target = new TestContext(source.user(), targetSpace, source.jwt());

        Document movedRoot = createDocument(source, "Moved Root", null);
        Document movedChild = createDocument(source, "Moved Child", movedRoot.getId());
        Document targetFirst = createDocument(target, "Target First", null);
        Document targetSecond = createDocument(target, "Target Second", null);

        move(source, movedRoot.getId(), targetSpace.getId(), null, 1);

        assertEquals(List.of(targetFirst.getId(), movedRoot.getId(), targetSecond.getId()),
                ids(tree(targetSpace.getId())));
        assertEquals(targetSpace.getId(), documentService.getDocumentById(movedRoot.getId()).getSpaceId());
        assertEquals(targetSpace.getId(), documentService.getDocumentById(movedChild.getId()).getSpaceId());
        assertEquals(movedRoot.getId(), documentService.getDocumentById(movedChild.getId()).getParentDocumentId());
    }

    @Test
    void parentCannotBeMovedInsideItsDescendant() throws Exception {
        TestContext context = createContext("cycle-order");
        Document parent = createDocument(context, "Parent", null);
        Document child = createDocument(context, "Child", parent.getId());

        String body = """
                {"spaceId":%d,"parentId":%d,"position":0}
                """.formatted(context.space().getId(), child.getId());

        mockMvc.perform(post("/api/documents/" + parent.getId() + "/move")
                        .cookie(jwtCookie(context.jwt()))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    private TestContext createContext(String prefix) throws Exception {
        String login = uniqueLogin(prefix);
        User user = persistUser(login, "password123", login + "@kb.local", GlobalRole.EDITOR, true);
        String jwt = loginAndGetJwt(login, "password123");
        Space space = spaceRepository.save(Space.create(prefix + "-" + System.nanoTime(), "", user.getId()));
        return new TestContext(user, space, jwt);
    }

    private Document createDocument(TestContext context, String title, Long parentId) {
        return documentService.createDocument(
                title + " " + System.nanoTime(), "", context.space().getId(), parentId,
                context.user().getId(), null);
    }

    private void move(TestContext context, Long documentId, Long spaceId, Long parentId, int position)
            throws Exception {
        String parentJson = parentId == null ? "null" : parentId.toString();
        String body = """
                {"spaceId":%d,"parentId":%s,"position":%d}
                """.formatted(spaceId, parentJson, position);

        mockMvc.perform(post("/api/documents/" + documentId + "/move")
                        .cookie(jwtCookie(context.jwt()))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private List<DocumentTreeNode> tree(Long spaceId) {
        return documentService.getHierarchiesForSpaces(List.of(spaceId)).getOrDefault(spaceId, List.of());
    }

    private DocumentTreeNode findNode(List<DocumentTreeNode> nodes, Long documentId) {
        return nodes.stream()
                .filter(node -> node.getDocument().getId().equals(documentId))
                .findFirst()
                .orElseThrow();
    }

    private List<Long> ids(List<DocumentTreeNode> nodes) {
        return nodes.stream().map(node -> node.getDocument().getId()).toList();
    }

    private List<Integer> sortOrders(List<DocumentTreeNode> nodes) {
        return nodes.stream().map(node -> node.getDocument().getSortOrder()).toList();
    }

    private record TestContext(User user, Space space, String jwt) {}
}
